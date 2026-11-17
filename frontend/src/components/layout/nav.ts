import {
  ClipboardCheck, FilePlus2, FileText, History, Inbox, LayoutDashboard, ListChecks, Scale, Settings,
  UserRound, Users, Workflow, type LucideIcon,
} from 'lucide-react';
import type { Role } from '@/lib/types';

export interface NavItem { to: string; label: string; short: string; icon: LucideIcon; end?: boolean }
export interface NavSection { title: string; items: NavItem[] }

export const NAV_BY_ROLE: Record<Role, NavSection[]> = {
  SUPERVISOR: [
    {
      title: 'Operations',
      items: [
        { to: '/', label: 'Overview Dashboard', short: 'Overview', icon: LayoutDashboard, end: true },
        { to: '/triage', label: 'Triage Queue', short: 'Triage', icon: ClipboardCheck },
        { to: '/routing', label: 'SmartAssign Routing', short: 'Routing', icon: Workflow },
        { to: '/claims', label: 'All Claims', short: 'Claims', icon: FileText },
        { to: '/handlers', label: 'Handler Management', short: 'Handlers', icon: Users },
      ],
    },
    {
      title: 'Insight',
      items: [
        { to: '/fairness', label: 'Fairness Monitoring', short: 'Fairness', icon: Scale },
        { to: '/audit', label: 'Assignment History & Audit', short: 'Audit', icon: History },
        { to: '/settings', label: 'Settings', short: 'Settings', icon: Settings },
      ],
    },
  ],
  CUSTOMER: [
    {
      title: 'My claims',
      items: [
        { to: '/customer', label: 'My Claims', short: 'Claims', icon: Inbox, end: true },
        { to: '/customer/new', label: 'Report a Claim', short: 'Report', icon: FilePlus2 },
      ],
    },
  ],
  HANDLER: [
    {
      title: 'My work',
      items: [
        { to: '/handler', label: 'Assigned Claims', short: 'Assigned', icon: ListChecks, end: true },
        { to: '/handler/resolved', label: 'Resolved Claims', short: 'Resolved', icon: ClipboardCheck },
        { to: '/handler/profile', label: 'My Profile & Availability', short: 'Profile', icon: UserRound },
      ],
    },
  ],
};

export const PORTAL_LABEL: Record<Role, string> = {
  CUSTOMER: 'Customer portal',
  SUPERVISOR: 'Supervisor portal',
  HANDLER: 'Handler portal',
};

/** Breadcrumb labels for path segments. */
export const SEGMENT_LABEL: Record<string, string> = {
  claims: 'All Claims',
  triage: 'Triage Queue',
  handlers: 'Handler Management',
  routing: 'SmartAssign Routing',
  audit: 'Assignment History & Audit',
  fairness: 'Fairness Monitoring',
  settings: 'Settings',
  customer: 'My Claims',
  new: 'Report a Claim',
  handler: 'Assigned Claims',
  resolved: 'Resolved Claims',
  profile: 'My Profile & Availability',
};
