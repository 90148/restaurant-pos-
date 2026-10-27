import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';
import { useQuery } from '@tanstack/react-query';
import { outletApi } from '@/api/outlets';
import {
  LogOut,
  User as UserIcon,
  Store,
  ChevronDown,
  UtensilsCrossed,
  ExternalLink,
  BellRing,
  Menu,
} from 'lucide-react';
import Button from '@/components/common/Button';
import { getServiceRequests } from '@/services/customerService';

interface NavbarProps {
  onMenuClick: () => void;
}

export const Navbar: React.FC<NavbarProps> = ({ onMenuClick }) => {
  const { user, logout, activeOutletId, setActiveOutlet } = useAuth();
  const [pendingCalls, setPendingCalls] = useState(0);

  useEffect(() => {
    const checkCalls = () => {
      const requests = getServiceRequests();
      setPendingCalls(requests.filter((r) => r.status === 'PENDING').length);
    };
    checkCalls();

    window.addEventListener('restomaster_customer_data_updated', checkCalls);
    return () => {
      window.removeEventListener('restomaster_customer_data_updated', checkCalls);
    };
  }, []);

  const { data: activeOutlets = [] } = useQuery({
    queryKey: ['outlets-active'],
    queryFn: outletApi.getActiveOutlets,
  });

  const currentOutlet = activeOutlets.find((o) => o.id === activeOutletId);
  const displayName = currentOutlet?.name || user?.outletName || 'Select Outlet';

  return (
    <header className="h-16 bg-white border-b border-slate-200 px-3 sm:px-6 flex items-center justify-between gap-3 flex-shrink-0 z-20">
      <button
        type="button"
        aria-label="Open navigation menu"
        className="rounded-lg p-2 text-slate-600 hover:bg-slate-100 md:hidden"
        onClick={onMenuClick}
      >
        <Menu className="h-5 w-5" />
      </button>

      {/* Active Outlet Selector */}
      <div className="flex min-w-0 flex-1 items-center gap-3">
        <div className="relative flex min-w-0 max-w-full items-center bg-slate-100 px-3 py-1.5 rounded-lg border border-slate-200">
          <Store className="w-4 h-4 text-emerald-600 mr-2" />
          <select
            value={activeOutletId || ''}
            onChange={(e) => setActiveOutlet(e.target.value)}
            className="max-w-[9rem] bg-transparent text-xs font-semibold text-slate-800 pr-6 focus:outline-none appearance-none cursor-pointer sm:max-w-none"
          >
            {activeOutlets.length === 0 && <option value="">{displayName}</option>}
            {activeOutlets.map((outlet) => (
              <option key={outlet.id} value={outlet.id}>
                {outlet.name} ({outlet.code})
              </option>
            ))}
          </select>
          <ChevronDown className="w-3.5 h-3.5 text-slate-400 absolute right-2 pointer-events-none" />
        </div>

        <div className="hidden items-center gap-1.5 text-xs text-slate-500 lg:flex">
          <span className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse" />
          <span>Real-time Active</span>
        </div>
      </div>

      {/* User Actions & Customer App Switcher */}
      <div className="flex items-center gap-2 sm:gap-3">
        {/* Table Service Calls Alert Badge */}
        {pendingCalls > 0 && (
          <Link
            to="/customer-management"
            className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg bg-rose-50 hover:bg-rose-100 border border-rose-200 text-rose-700 text-xs font-bold transition-colors animate-pulse"
            title={`${pendingCalls} pending table assistance requests`}
          >
            <BellRing className="w-3.5 h-3.5 text-rose-600" />
            <span className="hidden sm:inline">{pendingCalls} Table Calls</span>
          </Link>
        )}

        {/* Customer Experience Portal Link */}
        <Link
          to="/customer/home"
          target="_blank"
          rel="noopener noreferrer"
          className="flex items-center gap-1.5 px-2 sm:px-3 py-1.5 text-xs font-semibold text-amber-700 bg-amber-50 hover:bg-amber-100 border border-amber-200 rounded-lg transition-colors shadow-sm group"
          title="Open Customer Dining Experience in a new tab"
        >
          <UtensilsCrossed className="w-3.5 h-3.5 text-amber-600 group-hover:rotate-12 transition-transform" />
          <span className="hidden lg:inline">Customer App</span>
          <ExternalLink className="w-3 h-3 text-amber-500" />
        </Link>

        <div className="flex items-center gap-3 text-right">
          <div className="hidden sm:block">
            <p className="text-xs font-semibold text-slate-800">{user?.fullName || user?.username || 'User'}</p>
            <p className="text-[10px] text-slate-500 uppercase">{user?.roles?.join(', ') || 'Staff'}</p>
          </div>
          <div className="w-8 h-8 rounded-full bg-slate-200 text-slate-700 flex items-center justify-center font-bold text-xs">
            <UserIcon className="w-4 h-4" />
          </div>
        </div>

        <Button
          variant="outline"
          size="sm"
          onClick={logout}
          leftIcon={<LogOut className="w-3.5 h-3.5" />}
          className="px-2 sm:px-3 text-rose-600 hover:text-rose-700 hover:bg-rose-50 border-rose-200"
        >
          <span className="hidden sm:inline">Logout</span>
        </Button>
      </div>
    </header>
  );
};

export default Navbar;
