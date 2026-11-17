import { describe, expect, it, vi, beforeEach } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { mockApi } from './mockApi';

vi.mock('@/lib/api', async (orig) => ({ ...(await orig<typeof import('@/lib/api')>()), api: mockApi }));
import App from '@/App';
import { renderApp } from './render';
import { ApiError } from '@/lib/api';

describe('sign in', () => {
  beforeEach(() => vi.clearAllMocks());

  it('validates required fields', async () => {
    renderApp(<App />, { route: '/login', signedIn: false });
    await userEvent.click(await screen.findByRole('button', { name: 'Sign in' }));
    expect(screen.getByText('Enter your email')).toBeInTheDocument();
    expect(mockApi.auth.login).not.toHaveBeenCalled();
  });

  it('shows the server message for wrong credentials', async () => {
    mockApi.auth.login.mockRejectedValue(new ApiError(401, 'Invalid email or password'));
    renderApp(<App />, { route: '/login', signedIn: false });
    await userEvent.type(await screen.findByLabelText(/Email/), 'someone@example.test');
    await userEvent.type(screen.getByLabelText(/^Password/), 'wrong');
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));
    expect(await screen.findByText('The email or password is incorrect.')).toBeInTheDocument();
  });

  it('sends each role to its own portal', async () => {
    const { customer } = await import('./fixtures');
    mockApi.auth.login.mockResolvedValue({ token: 't', tokenType: 'Bearer', expiresAt: '', user: customer });
    mockApi.customer.list.mockResolvedValue({ content: [], page: 0, size: 25, totalElements: 0, totalPages: 0 });
    renderApp(<App />, { route: '/login', signedIn: false });
    await userEvent.type(await screen.findByLabelText(/Email/), 'asha@example.test');
    await userEvent.type(screen.getByLabelText(/^Password/), 'Password-123');
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));
    expect(await screen.findByText("You haven't reported any claims yet")).toBeInTheDocument();
  });
});
