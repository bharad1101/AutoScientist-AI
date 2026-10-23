import React, { useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';

/**
 * HomeHero component for the public landing page.
 * Features the headline "Turn Data Into Discovery", primary & secondary CTAs,
 * a live scientific command console, and a lightweight canvas-based particle network.
 */
export default function HomeHero({ isAuthenticated }) {
  const navigate = useNavigate();
  const canvasRef = useRef(null);
  const heroRef = useRef(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;

    // Skip canvas animation in headless test environments (jsdom) or when reduced motion is preferred
    if (typeof navigator !== 'undefined' && navigator.userAgent && navigator.userAgent.includes('jsdom')) {
      return;
    }
    if (window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      return;
    }

    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    let animationFrameId;
    let isVisible = true;

    // Responsive canvas dimensions
    const resizeCanvas = () => {
      if (!canvas) return;
      canvas.width = canvas.offsetWidth;
      canvas.height = canvas.offsetHeight;
    };
    resizeCanvas();
    window.addEventListener('resize', resizeCanvas);

    // Particle nodes setup
    const nodeCount = 38;
    const nodes = [];
    const width = canvas.width || 800;
    const height = canvas.height || 600;

    for (let i = 0; i < nodeCount; i++) {
      nodes.push({
        x: Math.random() * width,
        y: Math.random() * height,
        vx: (Math.random() - 0.5) * 0.45,
        vy: (Math.random() - 0.5) * 0.45,
        radius: Math.random() * 2.2 + 1.2,
        color: i % 3 === 0 ? 'rgba(34, 211, 238, 0.85)' : i % 3 === 1 ? 'rgba(129, 140, 248, 0.75)' : 'rgba(167, 139, 250, 0.65)',
      });
    }

    // Animation loop
    const render = () => {
      if (!isVisible) return;
      ctx.clearRect(0, 0, canvas.width, canvas.height);

      // Draw connections
      const maxDistance = 120;
      for (let i = 0; i < nodes.length; i++) {
        for (let j = i + 1; j < nodes.length; j++) {
          const dx = nodes[i].x - nodes[j].x;
          const dy = nodes[i].y - nodes[j].y;
          const dist = Math.sqrt(dx * dx + dy * dy);

          if (dist < maxDistance) {
            const alpha = (1 - dist / maxDistance) * 0.22;
            ctx.beginPath();
            ctx.strokeStyle = `rgba(34, 211, 238, ${alpha})`;
            ctx.lineWidth = 0.8;
            ctx.moveTo(nodes[i].x, nodes[i].y);
            ctx.lineTo(nodes[j].x, nodes[j].y);
            ctx.stroke();
          }
        }
      }

      // Draw and update nodes
      for (let i = 0; i < nodes.length; i++) {
        const node = nodes[i];
        node.x += node.vx;
        node.y += node.vy;

        // Bounce at boundaries
        if (node.x < 0 || node.x > canvas.width) node.vx *= -1;
        if (node.y < 0 || node.y > canvas.height) node.vy *= -1;

        ctx.beginPath();
        ctx.arc(node.x, node.y, node.radius, 0, Math.PI * 2);
        ctx.fillStyle = node.color;
        ctx.shadowBlur = 6;
        ctx.shadowColor = 'rgba(34, 211, 238, 0.5)';
        ctx.fill();
        ctx.shadowBlur = 0;
      }

      animationFrameId = requestAnimationFrame(render);
    };

    // Pause animation when offscreen
    let observer;
    if ('IntersectionObserver' in window && heroRef.current) {
      observer = new IntersectionObserver(
        (entries) => {
          entries.forEach((entry) => {
            isVisible = entry.isIntersecting;
            if (isVisible) {
              cancelAnimationFrame(animationFrameId);
              animationFrameId = requestAnimationFrame(render);
            }
          });
        },
        { threshold: 0.1 }
      );
      observer.observe(heroRef.current);
    } else {
      animationFrameId = requestAnimationFrame(render);
    }

    return () => {
      window.removeEventListener('resize', resizeCanvas);
      if (observer) observer.disconnect();
      cancelAnimationFrame(animationFrameId);
    };
  }, []);

  const handleScrollToDiscovery = (e) => {
    e.preventDefault();
    const target = document.getElementById('chapter-raw-data');
    if (target) {
      target.scrollIntoView({ behavior: 'smooth' });
    }
  };

  return (
    <section ref={heroRef} className="hero-section text-white">
      <canvas ref={canvasRef} className="hero-canvas" aria-hidden="true" />

      <div className="container hero-content py-4">
        <div className="row align-items-center g-5">
          <div className="col-lg-6">
            <div className="hero-badge mb-3">
              <i className="bi bi-stars"></i>
              <span>Autonomous Scientific Research Pipeline</span>
            </div>

            <h1 className="hero-headline">
              Turn Data Into Discovery.
            </h1>

            <p className="hero-lead mb-4">
              Explore patterns, evaluate models, investigate explanations, and turn your datasets into evidence-backed scientific insights.
            </p>

            <div className="d-flex flex-wrap gap-3 align-items-center mb-4">
              <button
                className="btn-discovery-primary"
                onClick={() => navigate(isAuthenticated ? '/datasets' : '/register')}
              >
                <span>{isAuthenticated ? 'Start a Discovery' : 'Start a Discovery'}</span>
                <i className="bi bi-arrow-right"></i>
              </button>

              <a
                href="#chapter-raw-data"
                onClick={handleScrollToDiscovery}
                className="btn-discovery-secondary"
              >
                <i className="bi bi-compass"></i>
                <span>Explore the Science</span>
              </a>

              {isAuthenticated && (
                <button
                  className="btn btn-outline-info btn-sm ms-lg-2"
                  onClick={() => navigate('/dashboard')}
                >
                  <i className="bi bi-speedometer2 me-1"></i> Open Dashboard
                </button>
              )}
            </div>

            <div className="d-flex flex-wrap gap-3 pt-3 border-top border-white border-opacity-10 small">
              <span className="d-flex align-items-center gap-1 text-light opacity-90">
                <i className="bi bi-shield-check text-highlight-cyan"></i> 100-Point Quality Audit
              </span>
              <span className="d-flex align-items-center gap-1 text-light opacity-90">
                <i className="bi bi-diagram-3 text-highlight-cyan"></i> Bivariate Associations & p-values
              </span>
              <span className="d-flex align-items-center gap-1 text-light opacity-90">
                <i className="bi bi-cpu text-highlight-cyan"></i> Seeded, Reproducible AutoML
              </span>
              <span className="d-flex align-items-center gap-1 text-light opacity-90">
                <i className="bi bi-journal-check text-highlight-cyan"></i> Provenance Evidence Graph
              </span>
            </div>
          </div>

          <div className="col-lg-6">
            <div className="hero-terminal">
              <div className="terminal-header">
                <div className="terminal-dots">
                  <span className="terminal-dot red"></span>
                  <span className="terminal-dot yellow"></span>
                  <span className="terminal-dot green"></span>
                </div>
                <div className="small text-muted font-monospace d-flex align-items-center gap-2">
                  <i className="bi bi-terminal"></i>
                  <span>autoscientist-cli v2.4</span>
                </div>
              </div>
              <div className="terminal-body font-monospace">
                <div className="terminal-cmd">$ import cohort_biomarkers.xlsx --sheet "Clinical_Trial"</div>
                <div className="terminal-dim">✓ 5,412 rows × 18 columns profiled in 142ms</div>
                <div className="terminal-warning">⚠ isolated identifier: patient_id excluded from modeling</div>
                <div className="terminal-cmd mt-2">$ audit-quality --strict</div>
                <div className="terminal-success">✓ Quality Score: 98.4/100 (0 duplicates, 0 missing criticals)</div>
                <div className="terminal-cmd mt-2">$ associations --method pearson,spearman --min-effect 0.25</div>
                <div className="terminal-dim">✓ 7 significant bivariate associations discovered (p &lt; 0.001)</div>
                <div className="terminal-cmd mt-2">$ automl --target response_score --seed 42 --cross-val 5</div>
                <div className="terminal-success">✓ Best model: Random Forest (AUC: 0.892, F1: 0.841) persisted</div>
                <div className="terminal-cmd mt-2">$ explain --fairness disparate-impact --group treatment_arm</div>
                <div className="terminal-info">✓ EEOC Four-Fifths parity ratio: 0.88 &gt; 0.80 threshold (Passed)</div>
                <div className="terminal-cmd mt-2">$ report --format pdf,markdown,xlsx</div>
                <div className="terminal-success">→ Evidence graph compiled: publication-grade report ready</div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
