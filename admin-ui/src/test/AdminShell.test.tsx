import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import App from '../App';
import { RevealOnceModal } from '../components/RevealOnceModal';
import { GlobalSearchModal } from '../components/GlobalSearchModal';
import { KeyboardShortcutsModal } from '../components/KeyboardShortcutsModal';
import { SignedContextSnippetGenerator } from '../components/SignedContextSnippetGenerator';
import { ErrorExplanationBanner } from '../components/ErrorExplanationBanner';

describe('CRANIAL Admin UI Shell', () => {
  it('renders CRANIAL login console when unauthenticated', async () => {
    sessionStorage.clear();
    render(<App />);
    expect(await screen.findByText('CRANIAL')).toBeInTheDocument();
    expect(screen.getByText('Universal AI Integration Platform')).toBeInTheDocument();
    expect(screen.getByText(/One Brain\. Many Connections\./i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Sign In to Operator Console/i })).toBeInTheDocument();
  });

  it('requires explicit operator acknowledgement before closing reveal-once credential modal', () => {
    const onClose = vi.fn();
    render(
      <RevealOnceModal
        issued={{
          credential: {
            id: '11111111-1111-1111-1111-111111111111',
            application_id: '22222222-2222-2222-2222-222222222222',
            name: 'Peggy Key',
            environment: 'PROD',
            key_prefix: 'crn_prod_abc123def456',
            status: 'ACTIVE',
            created_at: new Date().toISOString(),
          },
          raw_api_key: 'crn_prod_abc123def456.secretTokenXYZ',
          raw_signing_secret: 'signingSecretTokenABC',
          warning: 'Store raw_api_key and raw_signing_secret securely now.',
        }}
        onClose={onClose}
      />
    );

    expect(screen.getByText('crn_prod_abc123def456.secretTokenXYZ')).toBeInTheDocument();
    const doneBtn = screen.getByRole('button', { name: 'Done' });
    expect(doneBtn).toBeDisabled();

    const ackBox = screen.getByRole('checkbox');
    fireEvent.click(ackBox);
    expect(doneBtn).toBeEnabled();

    fireEvent.click(doneBtn);
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('allows switching between English and Indonesian in dual language mode', async () => {
    sessionStorage.clear();
    localStorage.clear();
    render(<App />);
    expect(await screen.findByText('CRANIAL')).toBeInTheDocument();

    // Switch to Indonesian
    const idBtn = screen.getByRole('button', { name: 'ID' });
    fireEvent.click(idBtn);

    expect(screen.getByText('Platform Integrasi AI Universal')).toBeInTheDocument();
    expect(screen.getByText(/Satu Otak\. Banyak Koneksi\./i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Masuk ke Konsol Operator/i })).toBeInTheDocument();

    // Switch back to English
    const enBtn = screen.getByRole('button', { name: 'EN' });
    fireEvent.click(enBtn);

    expect(screen.getByText('Universal AI Integration Platform')).toBeInTheDocument();
    expect(screen.getByText(/One Brain\. Many Connections\./i)).toBeInTheDocument();
  });

  it('renders Documents & OCR console and switches sub-tabs in English and Indonesian', async () => {
    sessionStorage.setItem('cranial_admin_jwt', 'mock-jwt-token');
    localStorage.clear();

    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (url.endsWith('/auth/me')) {
        return new Response(
          JSON.stringify({
            success: true,
            data: {
              id: '00000000-0000-0000-0000-000000000001',
              email: 'admin@cranial.test',
              display_name: 'Operator',
              roles: ['SUPER_ADMIN'],
              permissions: ['app:read', 'app:write', 'capability:manage'],
            },
          }),
          { status: 200, headers: { 'Content-Type': 'application/json' } }
        );
      }
      return new Response(JSON.stringify({ success: true, data: [] }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      });
    });

    try {
      render(<App />);
      const docNav = await screen.findByRole('button', { name: 'Documents & OCR' });
      fireEvent.click(docNav);

      expect(await screen.findByText('Document Intelligence & OCR')).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'OCR Playground' })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /Human Review Queue/i })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'OCR Benchmarks' })).toBeInTheDocument();

      // Switch to Indonesian
      fireEvent.click(screen.getByRole('button', { name: 'ID' }));
      expect(await screen.findByText('Inteligensi Dokumen & OCR')).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Dokumen & OCR' })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /Antrean Tinjauan Manusia/i })).toBeInTheDocument();
    } finally {
      fetchMock.mockRestore();
      sessionStorage.clear();
    }
  });

  it('renders Knowledge & RAG console and switches between English and Indonesian', async () => {
    sessionStorage.setItem('cranial_admin_jwt', 'mock-jwt-token');
    localStorage.clear();

    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (url.endsWith('/auth/me')) {
        return new Response(
          JSON.stringify({
            success: true,
            data: {
              id: '00000000-0000-0000-0000-000000000001',
              email: 'admin@cranial.test',
              display_name: 'Operator',
              roles: ['SUPER_ADMIN'],
              permissions: ['app:read', 'app:write', 'capability:manage'],
            },
          }),
          { status: 200, headers: { 'Content-Type': 'application/json' } }
        );
      }
      return new Response(JSON.stringify({ success: true, data: [] }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      });
    });

    try {
      render(<App />);
      const ragNav = await screen.findByRole('button', { name: 'Knowledge & RAG' });
      fireEvent.click(ragNav);

      expect(await screen.findByText('Knowledge Intelligence & RAG')).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Collections & Wizard' })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Retrieval Debugger & Ask' })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'RAG Benchmarks' })).toBeInTheDocument();

      // Switch to Indonesian
      fireEvent.click(screen.getByRole('button', { name: 'ID' }));
      expect(await screen.findByText('Inteligensi Pengetahuan & RAG')).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Pengetahuan & RAG' })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Debugger Retrieval & Tanya' })).toBeInTheDocument();
    } finally {
      fetchMock.mockRestore();
      sessionStorage.clear();
    }
  });

  it('renders Data & Analytics console and switches between English and Indonesian', async () => {
    sessionStorage.setItem('cranial_admin_jwt', 'mock-jwt-token');
    localStorage.clear();

    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (url.endsWith('/auth/me')) {
        return new Response(
          JSON.stringify({
            status: 'ok',
            data: {
              id: '00000000-0000-0000-0000-000000000001',
              email: 'admin@cranial.test',
              display_name: 'Operator',
              roles: ['SUPER_ADMIN'],
              permissions: ['app:read', 'app:write', 'capability:manage'],
            },
          }),
          { status: 200, headers: { 'Content-Type': 'application/json' } }
        );
      }
      return new Response(JSON.stringify({ status: 'ok', data: [] }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      });
    });

    try {
      render(<App />);
      const analyticsNav = await screen.findByRole('button', { name: 'Data & Analytics' });
      fireEvent.click(analyticsNav);

      expect(await screen.findByText('Data Intelligence & Analytics')).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Analytics Explorer & NL Query' })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Budget vs Actual & Trends' })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Cache & Benchmarks' })).toBeInTheDocument();

      // Switch to Indonesian
      fireEvent.click(screen.getByRole('button', { name: 'ID' }));
      expect(await screen.findByText('Inteligensi Data & Analitika')).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Data & Analitika' })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Eksplorasi Analitika & Kueri Bahasa Alami' })).toBeInTheDocument();
    } finally {
      fetchMock.mockRestore();
      sessionStorage.clear();
    }
  });

  it('renders Decisions & Optimization console and switches between English and Indonesian', async () => {
    sessionStorage.setItem('cranial_admin_jwt', 'mock-jwt-token');
    localStorage.clear();

    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (url.endsWith('/auth/me')) {
        return new Response(
          JSON.stringify({
            status: 'ok',
            data: {
              id: '00000000-0000-0000-0000-000000000001',
              email: 'admin@cranial.test',
              display_name: 'Operator',
              roles: ['SUPER_ADMIN'],
              permissions: ['app:read', 'app:write', 'capability:manage'],
            },
          }),
          { status: 200, headers: { 'Content-Type': 'application/json' } }
        );
      }
      return new Response(JSON.stringify({ status: 'ok', data: [] }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      });
    });

    try {
      render(<App />);
      const decisionNav = await screen.findByRole('button', { name: 'Decisions & Optimization' });
      fireEvent.click(decisionNav);

      expect(await screen.findByText('Decision Intelligence & Optimization')).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Optimization & Scenarios' })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Human Approval Queue' })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Deterministic Benchmarks' })).toBeInTheDocument();

      // Switch to Indonesian
      fireEvent.click(screen.getByRole('button', { name: 'ID' }));
      expect(await screen.findByText('Inteligensi Keputusan & Optimasi')).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Keputusan & Optimasi' })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Optimasi & Skenario' })).toBeInTheDocument();
    } finally {
      fetchMock.mockRestore();
      sessionStorage.clear();
    }
  });

  it('renders Customer Intelligence console and switches between English and Indonesian', async () => {
    sessionStorage.setItem('cranial_admin_jwt', 'mock-jwt-token');
    localStorage.clear();

    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (url.endsWith('/auth/me')) {
        return new Response(
          JSON.stringify({
            status: 'ok',
            data: {
              id: '00000000-0000-0000-0000-000000000001',
              email: 'admin@cranial.test',
              display_name: 'Operator',
              roles: ['SUPER_ADMIN'],
              permissions: ['app:read', 'app:write', 'capability:manage'],
            },
          }),
          { status: 200, headers: { 'Content-Type': 'application/json' } }
        );
      }
      return new Response(JSON.stringify({ status: 'ok', data: [] }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      });
    });

    try {
      render(<App />);
      const customerNav = await screen.findByRole('button', { name: 'Customer Intelligence' });
      fireEvent.click(customerNav);

      expect(await screen.findByRole('heading', { name: /Customer Intelligence/i })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Live Triage & Sentiment' })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Human Escalation Queue' })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Reviews & Feature Discovery' })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Regression Benchmarks' })).toBeInTheDocument();

      // Switch to Indonesian
      fireEvent.click(screen.getByRole('button', { name: 'ID' }));
      expect(await screen.findByRole('heading', { name: /Intelijen Pelanggan/i })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Intelijen Pelanggan' })).toBeInTheDocument();
    } finally {
      fetchMock.mockRestore();
      sessionStorage.clear();
    }
  });

  it('renders Conversations & Assistant console and switches between English and Indonesian', async () => {
    sessionStorage.setItem('cranial_admin_jwt', 'mock-jwt-token');
    localStorage.clear();

    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (url.endsWith('/auth/me')) {
        return new Response(
          JSON.stringify({
            status: 'ok',
            data: {
              id: '00000000-0000-0000-0000-000000000001',
              email: 'admin@cranial.test',
              display_name: 'Operator',
              roles: ['SUPER_ADMIN'],
              permissions: ['app:read', 'app:write', 'capability:manage'],
            },
          }),
          { status: 200, headers: { 'Content-Type': 'application/json' } }
        );
      }
      return new Response(JSON.stringify({ status: 'ok', data: [] }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      });
    });

    try {
      render(<App />);
      const convNav = await screen.findByRole('button', { name: 'Conversations & Assistant' });
      fireEvent.click(convNav);

      expect(await screen.findByRole('heading', { name: /Conversational Intelligence/i })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Sessions Explorer' })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Interactive Assistant & Simulator' })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Indonesian Slang Normalizer' })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Non-Banking Safety Guard' })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Retention & Governance' })).toBeInTheDocument();

      // Switch to Indonesian
      fireEvent.click(screen.getByRole('button', { name: 'ID' }));
      expect(await screen.findByRole('heading', { name: /Intelijen Percakapan/i })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Penjelajah Sesi' })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Normalisasi Slang Indonesia' })).toBeInTheDocument();
    } finally {
      fetchMock.mockRestore();
      sessionStorage.clear();
    }
  });

  it('renders and filters Global Search Command Palette (Ctrl+K)', () => {
    const onSelectTab = vi.fn();
    const onClose = vi.fn();

    render(<GlobalSearchModal isOpen={true} onClose={onClose} onSelectTab={onSelectTab} />);

    expect(screen.getByPlaceholderText(/Search commands, applications, models/i)).toBeInTheDocument();
    expect(screen.getByText('Platform Overview')).toBeInTheDocument();

    // Type query
    const searchInput = screen.getByPlaceholderText(/Search commands, applications, models/i);
    fireEvent.change(searchInput, { target: { value: 'peggy' } });

    expect(screen.getByText('Peggy Financial Assistant')).toBeInTheDocument();

    // Click on Peggy result item
    fireEvent.click(screen.getByText('Peggy Financial Assistant'));
    expect(onSelectTab).toHaveBeenCalledWith('applications');
    expect(onClose).toHaveBeenCalled();
  });

  it('renders Keyboard Shortcuts cheatsheet and closes cleanly', () => {
    const onClose = vi.fn();
    render(<KeyboardShortcutsModal isOpen={true} onClose={onClose} />);

    expect(screen.getByText('Keyboard Shortcuts & Navigation')).toBeInTheDocument();
    expect(screen.getByText('Cmd / Ctrl + K')).toBeInTheDocument();
    expect(screen.getByText('Pure Money Mgmt')).toBeInTheDocument();

    // Click Got it button
    fireEvent.click(screen.getByRole('button', { name: 'Got it' }));
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('renders SignedContextSnippetGenerator and switches code snippets', () => {
    const mockApp = {
      id: 'app-001',
      code: 'peggy',
      name: 'Peggy Budgeting',
      environment: 'PROD' as const,
      status: 'ACTIVE' as const,
      rate_limit_rpm: 120,
      created_at: new Date().toISOString(),
    };
    const mockCreds = [
      {
        id: 'cred-001',
        application_id: 'app-001',
        name: 'Primary Key',
        environment: 'PROD' as const,
        key_prefix: 'crn_prod_123',
        status: 'ACTIVE' as const,
        created_at: new Date().toISOString(),
      },
    ];

    const { container } = render(<SignedContextSnippetGenerator app={mockApp} credentials={mockCreds} />);

    expect(screen.getByText('Signed-Context Test Snippet Generator')).toBeInTheDocument();

    // Default cURL
    expect(container.querySelector('pre')?.textContent).toContain('curl -X POST');
    expect(container.querySelector('pre')?.textContent).toContain('X-CRANIAL-APP-CODE: peggy');

    // Switch to Java 21
    fireEvent.click(screen.getByRole('button', { name: 'Java 21' }));
    expect(container.querySelector('pre')?.textContent).toContain('HmacSHA256');

    // Switch to TypeScript
    fireEvent.click(screen.getByRole('button', { name: 'TypeScript' }));
    expect(container.querySelector('pre')?.textContent).toContain('createHmac');
    expect(container.querySelector('pre')?.textContent).toContain('sendCranialSignedRequest');
  });

  it('renders ErrorExplanationBanner with operator guidance and correlation ID', () => {
    const onRetry = vi.fn();
    render(
      <ErrorExplanationBanner
        errorCode="TOOL_PERMISSION_DENIED"
        errorMessage="Worker is not entitled to execute order cancellation"
        correlationId="req-correl-998877"
        onRetry={onRetry}
      />
    );

    expect(screen.getByText('TOOL_PERMISSION_DENIED')).toBeInTheDocument();
    expect(screen.getByText(/lack the tool authority/i)).toBeInTheDocument();
    expect(screen.getByText(/Verify application capability entitlements/i)).toBeInTheDocument();
    expect(screen.getByText(/req-correl-998877/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Copy ID' })).toBeInTheDocument();

    // Click Retry
    fireEvent.click(screen.getByRole('button', { name: 'Retry Action' }));
    expect(onRetry).toHaveBeenCalledTimes(1);
  });
});

