import React from 'react';
import { useAuth } from '../context/AuthContext';
import HomeHero from '../components/home/HomeHero';
import DiscoveryProgress from '../components/home/DiscoveryProgress';
import DatasetStoryVisual from '../components/home/DatasetStoryVisual';
import AnalyticsStoryVisual from '../components/home/AnalyticsStoryVisual';
import ModelStoryVisual from '../components/home/ModelStoryVisual';
import EvidenceStoryVisual from '../components/home/EvidenceStoryVisual';
import ReportStoryVisual from '../components/home/ReportStoryVisual';
import '../styles/home.css';

/**
 * Public homepage: Dark-cinematic, scroll-storytelling experience.
 * Narrative progression:
 * - Living Scientific Universe Hero (Turn Data Into Discovery.)
 * - Chapter 1: Raw Data (Every discovery starts with data.)
 * - Chapter 2: Patterns Emerge (Find the patterns hiding in plain sight.)
 * - Chapter 3: Experiments and Models (Turn questions into experiments.)
 * - Chapter 4: Explain and Investigate (Understand what your models reveal.)
 * - Chapter 5: Findings and Reports (Make every result easier to examine. / Your next discovery starts with a dataset.)
 */
export default function LandingPage() {
  const { isAuthenticated } = useAuth();

  return (
    <div className="cinematic-home">
      <HomeHero isAuthenticated={isAuthenticated} />
      <DiscoveryProgress />

      <div id="how-it-works">
        <DatasetStoryVisual isAuthenticated={isAuthenticated} />
      </div>

      <div id="capabilities">
        <AnalyticsStoryVisual />
        <ModelStoryVisual />
        <EvidenceStoryVisual />
      </div>

      <ReportStoryVisual isAuthenticated={isAuthenticated} />
    </div>
  );
}
