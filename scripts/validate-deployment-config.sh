#!/usr/bin/env bash
set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$DIR"

echo "Validating deployment configuration..."

required_paths=(
  ".github/workflows/ci.yml"
  ".gitignore"
  ".dockerignore"
  "render.yaml"
  "backend/.dockerignore"
  "backend/.mvn/wrapper/maven-wrapper.properties"
  "backend/src/main/resources/db/migration/V1__baseline.sql"
  "backend/src/main/resources/db/migration/V2__provenance_delta.sql"
  "backend/src/main/resources/db/migration/V3__association_confidence_intervals.sql"
  "backend/src/main/resources/db/migration/V4__password_reset_tokens.sql"
  "backend/src/main/resources/db/migration/V5__jobs_and_model_artifacts.sql"
  "backend/src/main/resources/db/migration/V6__model_artifact_longblob.sql"
  "backend/src/main/resources/db/migration/V7__job_ownership_and_parameters.sql"
  "frontend/eslint.config.js"
  "frontend/vercel.json"
  "frontend/src/test/viteConfigBuildGuard.test.js"
)
for path in "${required_paths[@]}"; do
  if [ ! -f "$path" ]; then
    echo "Missing required release file: $path" >&2
    exit 1
  fi
done

node <<'NODE'
const fs = require('fs');
const assert = require('assert');

const vercel = JSON.parse(fs.readFileSync('frontend/vercel.json', 'utf8'));
assert.strictEqual(vercel.framework, 'vite');
assert.strictEqual(vercel.installCommand, 'npm ci');
assert.strictEqual(vercel.buildCommand, 'npm run build');
assert.strictEqual(vercel.outputDirectory, 'dist');
assert.ok(Array.isArray(vercel.rewrites));
assert.ok(vercel.rewrites.some(r => r.source === '/(.*)' && r.destination === '/index.html'));
assert.strictEqual(vercel.git.deploymentEnabled.main, false);
assert.strictEqual(vercel.git.deploymentEnabled['preview/verify-deployment'], false);

const render = fs.readFileSync('render.yaml', 'utf8');
const services = render.split(/^  - type: web$/m).slice(1);
for (const [name, branch] of [
  ['autoscientist-backend-preview', 'preview/verify-deployment'],
  ['autoscientist-backend-production', 'main'],
]) {
  const service = services.find(block => block.includes(`name: ${name}`));
  assert.ok(service, `render.yaml service missing: ${name}`);
  assert.ok(service.includes(`branch: ${branch}`));
  assert.ok(service.includes('runtime: docker'));
  assert.ok(service.includes('dockerContext: .'));
  assert.ok(service.includes('dockerfilePath: ./Dockerfile'));
  assert.ok(service.includes('healthCheckPath: /api/health/ready'));
  assert.ok(service.includes('autoDeployTrigger: off'));
  assert.ok(service.includes('mountPath: /app/uploads'));
  for (const key of [
    'SPRING_DATASOURCE_URL',
    'SPRING_DATASOURCE_USERNAME',
    'SPRING_DATASOURCE_PASSWORD',
    'JWT_SECRET',
    'APP_CORS_ALLOWED_ORIGINS',
    'FRONTEND_URL',
  ]) {
    assert.ok(service.includes(`key: ${key}`), `${name} missing env var: ${key}`);
  }
}

const workflow = fs.readFileSync('.github/workflows/ci.yml', 'utf8');
for (const required of [
  'java-version: "17"',
  'node-version: "22.13.0"',
  'RENDER_PREVIEW_DEPLOY_HOOK_URL',
  'RENDER_PRODUCTION_DEPLOY_HOOK_URL',
  'VERCEL_PREVIEW_DEPLOY_HOOK_URL',
  'VERCEL_PRODUCTION_DEPLOY_HOOK_URL',
  'actionlint',
  "github.ref == 'refs/heads/preview/verify-deployment'",
  "github.ref == 'refs/heads/main'",
  'group: deploy-preview',
  'group: deploy-production',
  'cancel-in-progress: false',
  'd.get("data",{}).get("status","")',
]) {
  assert.ok(workflow.includes(required), `ci.yml missing: ${required}`);
}
assert.ok(!/uses: (actions\/(checkout|setup-java|setup-node|upload-artifact))@v\d+/.test(workflow), 'CI actions must be pinned to immutable SHAs');
assert.ok(workflow.includes("github.event_name == 'push' && github.ref == 'refs/heads/main'"), 'production deploy must require a push to main');
assert.ok(workflow.includes("github.event_name == 'push' && github.ref == 'refs/heads/preview/verify-deployment'"), 'preview deploy must require a push to the preview branch');

const rootDockerfile = fs.readFileSync('Dockerfile', 'utf8');
const backendDockerfile = fs.readFileSync('backend/Dockerfile', 'utf8');
for (const [name, dockerfile] of [['Dockerfile', rootDockerfile], ['backend/Dockerfile', backendDockerfile]]) {
  assert.ok(dockerfile.includes('maven:3.9-eclipse-temurin-17'), `${name} must build with Java 17`);
  assert.ok(dockerfile.includes('eclipse-temurin:17-jre'), `${name} must run on Java 17`);
  assert.ok(dockerfile.includes('useradd'), `${name} must create a non-root runtime user`);
  assert.ok(dockerfile.includes('/app/uploads/datasets/temp'), `${name} must create the durable temp upload directory`);
}
for (const host of ['localhost', '127.0.0.1']) {
  assert.ok(!render.includes(host), `render.yaml must not contain ${host}`);
}
const deployBlocks = workflow.split('  deploy-').slice(1).join('\n');
assert.ok(!/https?:\/\/(localhost|127\.0\.0\.1)/i.test(deployBlocks), 'deploy jobs must not accept localhost health URLs');

const wrapper = fs.readFileSync('backend/.mvn/wrapper/maven-wrapper.properties', 'utf8');
assert.ok(/^distributionSha256Sum=[0-9a-f]{64}$/m.test(wrapper));

const packageJson = JSON.parse(fs.readFileSync('frontend/package.json', 'utf8'));
assert.ok(packageJson.engines && packageJson.engines.node);
const lockRoot = JSON.parse(fs.readFileSync('frontend/package-lock.json', 'utf8')).packages[''];
assert.strictEqual(lockRoot.engines.node, packageJson.engines.node);

console.log('Deployment configuration is internally consistent.');
NODE

if git ls-files | grep -E '(^|/)\.env$|\.(pem|key|p12|pfx|jks|keystore)$|(^|/)(backups|uploads|target|dist|node_modules)/|\.mv\.db$'; then
  echo 'Tracked secret, backup, upload, database, or generated artifact detected.' >&2
  exit 1
fi

local_api_pattern='https?://(localhost|127\.0\.0\.1):(8080|5173|3000)'

if grep -R -n -E "$local_api_pattern" frontend/src --exclude-dir=test; then
  echo 'Hardcoded local API reference found in frontend application code.' >&2
  exit 1
fi

if [ -d frontend/dist ] && grep -R -n -E "$local_api_pattern" frontend/dist; then
  echo 'Hardcoded local API reference found in built frontend output.' >&2
  exit 1
fi

echo 'Deployment configuration validation passed.'
