import React, { useEffect, useState } from 'react';

/**
 * Storytelling progress bar for the public landing page.
 * Tracks the visitor's progress through the five discovery chapters:
 * 1. Raw Data
 * 2. Patterns Emerge
 * 3. Experiments & Models
 * 4. Explain & Investigate
 * 5. Findings & Reports
 */
export default function DiscoveryProgress() {
  const [activeChapter, setActiveChapter] = useState('chapter-raw-data');

  const chapters = [
    { id: 'chapter-raw-data', num: '01', title: 'Raw Data' },
    { id: 'chapter-patterns', num: '02', title: 'Patterns Emerge' },
    { id: 'chapter-models', num: '03', title: 'Experiments & Models' },
    { id: 'chapter-explain', num: '04', title: 'Explain & Investigate' },
    { id: 'chapter-reports', num: '05', title: 'Findings & Reports' },
  ];

  useEffect(() => {
    if (!('IntersectionObserver' in window)) return;

    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            setActiveChapter(entry.target.id);
          }
        });
      },
      { rootMargin: '-20% 0px -60% 0px', threshold: 0 }
    );

    chapters.forEach((ch) => {
      const el = document.getElementById(ch.id);
      if (el) observer.observe(el);
    });

    return () => observer.disconnect();
  }, []);

  const scrollTo = (id) => (e) => {
    e.preventDefault();
    const el = document.getElementById(id);
    if (el) {
      el.scrollIntoView({ behavior: 'smooth' });
    }
  };

  return (
    <div className="story-progress-bar" role="region" aria-label="Discovery chapters">
      <div className="container">
        <ul className="story-nav-list">
          {chapters.map((ch) => (
            <li key={ch.id}>
              <a
                href={`#${ch.id}`}
                onClick={scrollTo(ch.id)}
                className={`story-nav-btn ${activeChapter === ch.id ? 'active' : ''}`}
                aria-current={activeChapter === ch.id ? 'step' : undefined}
              >
                <span className="story-nav-num">{ch.num}.</span>
                <span>{ch.title}</span>
              </a>
            </li>
          ))}
        </ul>
      </div>
    </div>
  );
}
