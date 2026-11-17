import { useNavigate } from 'react-router-dom';
import { ChevronDown, LogOut, Settings, ShieldCheck } from 'lucide-react';
import { MenuItem, Popover } from '@/components/ui/dropdown';
import { useAuth } from '@/lib/auth';
import { initials, titleCase } from '@/lib/format';
import { useToast } from '@/components/ui/toast';

export function UserMenu() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const { notify } = useToast();
  if (!user) return null;
  return (
    <Popover label="Account" trigger={({ open, toggle }) => (
      <button type="button" onClick={toggle} aria-expanded={open} aria-haspopup="menu"
        className="flex cursor-pointer items-center gap-2.5 rounded-md py-1 pl-1 pr-2 hover:bg-navy-50">
        <span className="flex size-8 items-center justify-center rounded-full bg-navy-900 text-[12px] font-semibold text-white">
          {initials(user.fullName)}
        </span>
        <span className="hidden text-left leading-tight md:block">
          <span className="block text-[13px] font-medium text-ink">{user.fullName}</span>
          <span className="block text-[11.5px] text-ink-subtle">{titleCase(user.role)}</span>
        </span>
        <ChevronDown className="hidden size-4 text-ink-subtle md:block" />
      </button>
    )}>
      {(close) => (
        <>
          <div className="px-3 py-2.5">
            <p className="text-[13px] font-semibold text-ink">{user.fullName}</p>
            <p className="text-[12px] text-ink-muted">{user.email}</p>
            <p className="mt-1.5 inline-flex items-center gap-1 rounded bg-navy-50 px-1.5 py-0.5 text-[11px] font-medium text-navy-700">
              <ShieldCheck className="size-3" /> {titleCase(user.role)}
            </p>
          </div>
          <div className="my-1 h-px bg-border" />
          {user.role === 'SUPERVISOR' && (
            <MenuItem icon={<Settings />} onClick={() => { close(); navigate('/settings'); }}>Settings</MenuItem>
          )}
          {user.role === 'HANDLER' && (
            <MenuItem icon={<Settings />} onClick={() => { close(); navigate('/handler/profile'); }}>My profile</MenuItem>
          )}
          <MenuItem icon={<LogOut />} tone="danger" onClick={async () => {
            close();
            await logout();
            notify('info', 'You have been signed out');
            navigate('/login', { replace: true });
          }}>Sign out</MenuItem>
        </>
      )}
    </Popover>
  );
}
