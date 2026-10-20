import React, { useEffect, useState } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { getBillById, verifyStripePayment } from '@/api/billing';
import { Bill } from '@/types/billing';
import { Button } from '@/components/common/Button';
import { ReceiptModal } from '@/pages/billing/ReceiptModal';
import {
  CheckCircle2,
  Clock,
  AlertCircle,
  Receipt,
  ArrowLeft,
  RotateCcw,
  ExternalLink,
} from 'lucide-react';

export const StripeReturnPage: React.FC = () => {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();

  const billId = searchParams.get('billId');
  const sessionId = searchParams.get('session_id');

  const [bill, setBill] = useState<Bill | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [errorMsg, setErrorMsg] = useState<string>('');
  const [showReceiptModal, setShowReceiptModal] = useState<boolean>(false);
  const [pollingCount, setPollingCount] = useState<number>(0);

  const fetchBillStatus = async () => {
    if (!billId) {
      setErrorMsg('No bill ID provided in the return URL.');
      setIsLoading(false);
      return;
    }

    try {
      setIsLoading(true);
      setErrorMsg('');

      // If we have a sessionId, first attempt direct verification against backend & Stripe API
      if (sessionId) {
        try {
          const verified = await verifyStripePayment(sessionId, billId);
          setBill(verified);
          setIsLoading(false);
          return;
        } catch {
          // If verifyStripePayment throws or hasn't finished, fall back to checking live bill status
        }
      }

      const data = await getBillById(billId);
      setBill(data);
    } catch (err: unknown) {
      const error = err as { response?: { data?: { message?: string } } };
      setErrorMsg(error?.response?.data?.message || 'Failed to retrieve bill status.');
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchBillStatus();
  }, [billId]);

  // If bill is not yet marked as PAID, poll up to 3 times in 2-second intervals
  // to give Stripe webhooks a moment to deliver to the backend
  useEffect(() => {
    if (bill && bill.status !== 'PAID' && pollingCount < 3) {
      const timer = setTimeout(() => {
        setPollingCount((prev) => prev + 1);
        getBillById(bill.id)
          .then((updated) => setBill(updated))
          .catch(() => {});
      }, 2000);
      return () => clearTimeout(timer);
    }
  }, [bill, pollingCount]);

  const isPaid = bill?.status === 'PAID';
  const isPartial = bill?.status === 'PARTIALLY_PAID';

  return (
    <div className="max-w-2xl mx-auto py-12 px-4">
      <div className="bg-white rounded-2xl shadow-xl border border-slate-200 overflow-hidden">
        {/* Header Banner */}
        <div
          className={`p-6 text-white text-center ${
            isPaid
              ? 'bg-emerald-600'
              : isPartial
              ? 'bg-amber-600'
              : errorMsg
              ? 'bg-rose-600'
              : 'bg-slate-800'
          }`}
        >
          {isLoading ? (
            <div className="flex flex-col items-center gap-2">
              <Clock className="w-12 h-12 animate-spin text-white/80" />
              <h1 className="text-xl font-black">Verifying Payment with Stripe...</h1>
              <p className="text-sm text-white/80">Confirming webhook settlement on authoritative ledger</p>
            </div>
          ) : isPaid ? (
            <div className="flex flex-col items-center gap-2">
              <CheckCircle2 className="w-14 h-14 text-white" />
              <h1 className="text-2xl font-black">Payment Successfully Settled!</h1>
              <p className="text-sm text-emerald-100">
                Stripe transaction confirmed and ledger updated.
              </p>
            </div>
          ) : isPartial ? (
            <div className="flex flex-col items-center gap-2">
              <Clock className="w-14 h-14 text-white" />
              <h1 className="text-2xl font-black">Partial Payment Received</h1>
              <p className="text-sm text-amber-100">
                Remaining balance is still outstanding.
              </p>
            </div>
          ) : errorMsg ? (
            <div className="flex flex-col items-center gap-2">
              <AlertCircle className="w-14 h-14 text-white" />
              <h1 className="text-2xl font-black">Unable to Verify Bill</h1>
              <p className="text-sm text-rose-100">{errorMsg}</p>
            </div>
          ) : (
            <div className="flex flex-col items-center gap-2">
              <Clock className="w-14 h-14 text-white animate-pulse" />
              <h1 className="text-2xl font-black">Payment Processing</h1>
              <p className="text-sm text-slate-200">
                Awaiting Stripe webhook confirmation.
              </p>
            </div>
          )}
        </div>

        {/* Body Details */}
        <div className="p-6 space-y-6">
          {bill && (
            <div className="space-y-4">
              <div className="bg-slate-50 rounded-xl p-4 border border-slate-200 grid grid-cols-2 gap-4 text-sm">
                <div>
                  <span className="text-xs text-slate-500 font-bold uppercase block">Bill Number</span>
                  <span className="font-black text-slate-800 text-base">{bill.billNumber}</span>
                </div>
                <div>
                  <span className="text-xs text-slate-500 font-bold uppercase block">Order Number</span>
                  <span className="font-bold text-slate-700">{bill.orderNumber}</span>
                </div>
                <div>
                  <span className="text-xs text-slate-500 font-bold uppercase block">Outlet</span>
                  <span className="font-semibold text-slate-700">{bill.outletName}</span>
                </div>
                <div>
                  <span className="text-xs text-slate-500 font-bold uppercase block">Table</span>
                  <span className="font-semibold text-slate-700">
                    {bill.tableNumber ? `Table ${bill.tableNumber}` : 'Takeaway / Delivery'}
                  </span>
                </div>
              </div>

              {/* Financial Summary */}
              <div className="border border-slate-200 rounded-xl p-4 divide-y divide-slate-100 text-sm">
                <div className="flex justify-between py-2">
                  <span className="text-slate-600">Total Amount:</span>
                  <span className="font-black text-slate-800">${Number(bill.totalAmount).toFixed(2)}</span>
                </div>
                <div className="flex justify-between py-2">
                  <span className="text-slate-600">Amount Paid:</span>
                  <span className="font-black text-emerald-600">${Number(bill.paidAmount).toFixed(2)}</span>
                </div>
                <div className="flex justify-between py-2">
                  <span className="text-slate-600">Outstanding Balance:</span>
                  <span className="font-black text-slate-800">${Number(bill.balanceAmount).toFixed(2)}</span>
                </div>
              </div>

              {sessionId && (
                <div className="text-xs text-slate-500 bg-slate-100 p-2.5 rounded-lg flex items-center justify-between">
                  <span className="font-mono truncate">Stripe Session: {sessionId}</span>
                  <ExternalLink className="w-3.5 h-3.5 text-slate-400 flex-shrink-0 ml-2" />
                </div>
              )}
            </div>
          )}

          {/* Action Buttons */}
          <div className="flex items-center justify-between pt-4 border-t border-slate-200">
            <Button
              variant="outline"
              onClick={() => navigate('/billing')}
              className="gap-2"
            >
              <ArrowLeft className="w-4 h-4" />
              Back to Billing List
            </Button>

            <div className="flex items-center gap-2">
              {!isPaid && (
                <Button
                  variant="outline"
                  onClick={fetchBillStatus}
                  disabled={isLoading}
                  className="gap-2"
                >
                  <RotateCcw className={`w-4 h-4 ${isLoading ? 'animate-spin' : ''}`} />
                  Refresh
                </Button>
              )}

              {isPaid && bill && (
                <Button
                  variant="primary"
                  onClick={() => setShowReceiptModal(true)}
                  className="gap-2"
                >
                  <Receipt className="w-4 h-4" />
                  View &amp; Print Receipt
                </Button>
              )}
            </div>
          </div>
        </div>
      </div>

      {/* Printable Receipt Modal */}
      {showReceiptModal && bill && (
        <ReceiptModal
          bill={bill}
          onClose={() => setShowReceiptModal(false)}
        />
      )}
    </div>
  );
};
