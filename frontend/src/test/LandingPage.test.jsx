import React from 'react';
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import LandingPage from '../pages/LandingPage';
import Navbar from '../components/Navbar';
import * as AuthContextModule from '../context/AuthContext';
import * as WorkflowContextModule from '../context/WorkflowContext';

vi.mock('../context/AuthContext', () => ({
  useAuth: vi.fn(),
}));

vi.mock('../context/WorkflowContext', () => ({
  useWorkflow: vi.fn(),
}));

describe('Landing Page & Branding (Requirement 1 & Part B)', () => {
  it('renders a single AutoScientist AI brand in the primary header and no duplicate header bar', () => {
    AuthContextModule.useAuth.mockReturnValue({
      user: null,
      isAuthenticated: false,
      logout: vi.fn(),
    });
    WorkflowContextModule.useWorkflow.mockReturnValue({
      dataset: null,
      hasDatasets: false,
      datasetsLoading: false,
    });

    render(
      <MemoryRouter initialEntries={['/']}>
        <div>
          <Navbar />
          <LandingPage />
        </div>
      </MemoryRouter>
    );

    // Primary Navbar contains the AutoScientist AI branding (single nav bar)
    const navbars = screen.getAllByRole('navigation');
    expect(navbars.length).toBe(1);

    // Navbar has Login, Register, How It Works, and Scientific Capabilities links
    expect(screen.getByRole('link', { name: /login/i })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /register/i })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /how it works/i })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /scientific capabilities/i })).toBeInTheDocument();

    // Cinematic hero headline
    expect(
      screen.getByRole('heading', {
        name: /turn data into discovery\./i,
      })
    ).toBeInTheDocument();

    // Primary and secondary CTAs
    const startDiscoveryBtns = screen.getAllByRole('button', { name: /start a discovery/i });
    expect(startDiscoveryBtns.length).toBeGreaterThan(0);
    expect(screen.getByRole('link', { name: /explore the science/i })).toBeInTheDocument();

    // The five storytelling chapters are present
    expect(
      screen.getByRole('heading', { name: /every discovery starts with data\./i })
    ).toBeInTheDocument();
    expect(
      screen.getByRole('heading', { name: /find the patterns hiding in plain sight\./i })
    ).toBeInTheDocument();
    expect(
      screen.getByRole('heading', { name: /turn questions into experiments\./i })
    ).toBeInTheDocument();
    expect(
      screen.getByRole('heading', { name: /understand what your models reveal\./i })
    ).toBeInTheDocument();
    expect(
      screen.getByRole('heading', { name: /make every result easier to examine\./i })
    ).toBeInTheDocument();

    // Concluding headline
    expect(
      screen.getByRole('heading', { name: /your next discovery starts with a dataset\./i })
    ).toBeInTheDocument();

    // Scientific integrity notice is displayed
    expect(
      screen.getByText(/model explanations and feature-response curves are analytical diagnostic tools, not proof of causation/i)
    ).toBeInTheDocument();

    // Explicit illustrative labels are rendered
    const illustrativeBadges = screen.getAllByText(/illustrative/i);
    expect(illustrativeBadges.length).toBeGreaterThan(0);
  });

  it('renders dashboard button for signed-in users on landing page', () => {
    AuthContextModule.useAuth.mockReturnValue({
      user: { name: 'Dr. Marie Curie', email: 'marie@curie.org' },
      isAuthenticated: true,
      logout: vi.fn(),
    });
    WorkflowContextModule.useWorkflow.mockReturnValue({
      dataset: null,
      hasDatasets: false,
      datasetsLoading: false,
    });

    render(
      <MemoryRouter initialEntries={['/']}>
        <div>
          <Navbar />
          <LandingPage />
        </div>
      </MemoryRouter>
    );

    // Open Dashboard button is available for authenticated user
    expect(
      screen.getByRole('button', { name: /open dashboard/i })
    ).toBeInTheDocument();
    // Register link is not shown in the navbar for authenticated users
    expect(screen.queryByRole('link', { name: /register/i })).not.toBeInTheDocument();
  });

  it('allows interactive tab switching and what-if adjustments', () => {
    AuthContextModule.useAuth.mockReturnValue({
      user: null,
      isAuthenticated: false,
      logout: vi.fn(),
    });
    WorkflowContextModule.useWorkflow.mockReturnValue({
      dataset: null,
      hasDatasets: false,
      datasetsLoading: false,
    });

    render(
      <MemoryRouter initialEntries={['/']}>
        <LandingPage />
      </MemoryRouter>
    );

    // Chapter 2: Tab switching
    const associationsTab = screen.getByRole('button', { name: /ranked associations/i });
    fireEvent.click(associationsTab);
    expect(screen.getByText(/Variable Pair/i)).toBeInTheDocument();

    const boxplotTab = screen.getByRole('button', { name: /group box plots/i });
    fireEvent.click(boxplotTab);
    expect(screen.getAllByText(/Control \(Placebo\)/i).length).toBeGreaterThan(0);

    // Chapter 3: What-If slider
    const slider = screen.getByLabelText(/what-if biomarker simulator slider/i);
    fireEvent.change(slider, { target: { value: '85' } });
    expect(screen.getByText(/85 μg\/L/i)).toBeInTheDocument();
  });
});
