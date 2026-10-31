import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import App from '../App';
import { RevealOnceModal } from '../components/RevealOnceModal';

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
});

