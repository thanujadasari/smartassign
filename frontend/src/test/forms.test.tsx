import { describe, expect, it } from 'vitest';
import { validateClaim } from '@/pages/customer/ReportClaimPage';
import { validateAccount } from '@/pages/auth/SignupPage';

const baseClaim = {
  claimType: 'AUTO' as const, policyType: 'AUTO', policyNumber: '0001', incidentType: 'COLLISION', incidentDate: '2026-11-01',
  location: 'Pune', estimatedLoss: '150000', injuryInvolved: false, thirdPartyInvolved: true, ongoingRisk: false,
  description: 'Rear-ended at a traffic signal; bumper damaged.', contactPhone: '',
};

describe('report-a-claim validation', () => {
  it('accepts a complete claim', () => expect(validateClaim(baseClaim)).toEqual({}));
  it('accepts a policy number typed with or without the P prefix, and rejects letters', () => {
    expect(validateClaim({ ...baseClaim, policyNumber: 'p0001' })).toEqual({});
    expect(Object.keys(validateClaim({ ...baseClaim, policyNumber: 'MOT-1' }))).toEqual(['policyNumber']);
  });
  it('rejects zero loss, missing city, a future date and a short description', () => {
    const e = validateClaim({ ...baseClaim, estimatedLoss: '0', location: '', incidentDate: '2999-01-01', description: 'short' });
    expect(Object.keys(e).sort()).toEqual(['description', 'estimatedLoss', 'incidentDate', 'location']);
  });
});

describe('sign-up validation', () => {
  const base = { fullName: 'Asha Kulkarni', email: 'asha@example.test', phone: '', password: 'Password-123', confirm: 'Password-123' };
  it('accepts a complete account', () => expect(validateAccount(base)).toEqual({}));
  it('rejects a bad email, a short password and a mismatch', () => {
    const e = validateAccount({ ...base, email: 'nope', password: 'short', confirm: 'other' });
    expect(Object.keys(e).sort()).toEqual(['confirm', 'email', 'password']);
  });
});
