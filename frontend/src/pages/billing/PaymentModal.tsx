import React, { useState } from 'react';
import { Bill, PaymentMethod } from '@/types/billing';
import { processPayment, createCheckoutSession, verifyRazorpayPayment } from '@/api/billing';
import { Button } from '@/components/common/Button';
import {
  CreditCard,
  Banknote,
  QrCode,
  Wallet,
  CheckCircle2,
  X,
  Coins,
  AlertCircle,
  Globe,
  Zap,
  ExternalLink,
  ShieldCheck,
  Loader2,
} from 'lucide-react';

interface PaymentModalProps {
  bill: Bill | null;
  onClose: () => void;
  onPaymentSuccess: (updatedBill: Bill) => void;
}

interface RazorpayResponse {
  razorpay_order_id: string;
  razorpay_payment_id: string;
  razorpay_signature: string;
}

const loadRazorpayScript = (): Promise<boolean> => {
  return new Promise((resolve) => {
    if ((window as unknown as { Razorpay?: unknown }).Razorpay) {
      resolve(true);
      return;
    }
    const script = document.createElement('script');
    script.src = 'https://checkout.razorpay.com/v1/checkout.js';
    script.async = true;
    script.onload = () => resolve(true);
    script.onerror = () => resolve(false);
    document.body.appendChild(script);
  });
};

export const PaymentModal: React.FC<PaymentModalProps> = ({
  bill,
  onClose,
  onPaymentSuccess,
}) => {
  if (!bill) return null;

  const remainingBalance = Number(bill.balanceAmount);

  const [paymentMethod, setPaymentMethod] = useState<PaymentMethod>('CASH');
  const [payAmount, setPayAmount] = useState<string>(remainingBalance.toFixed(2));
  const [tenderedAmount, setTenderedAmount] = useState<string>(remainingBalance.toFixed(2));
  const [transactionRef, setTransactionRef] = useState<string>('');
  const [notes, setNotes] = useState<string>('');
  const [isSubmitting, setIsSubmitting] = useState<boolean>(false);
  const [errorMsg, setErrorMsg] = useState<string>('');

  const isOnlineGateway = paymentMethod === 'STRIPE' || paymentMethod === 'RAZORPAY';
  const numPayAmount = isOnlineGateway ? remainingBalance : (parseFloat(payAmount) || 0);
  const numTendered = parseFloat(tenderedAmount) || 0;
  const changeAmount = paymentMethod === 'CASH' && numTendered > numPayAmount
    ? numTendered - numPayAmount
    : 0;

  const handleQuickCash = (amount: number) => {
    setTenderedAmount(amount.toFixed(2));
  };

  const handleMethodSelect = (method: PaymentMethod) => {
    setPaymentMethod(method);
    setErrorMsg('');
    if (method === 'STRIPE' || method === 'RAZORPAY') {
      setPayAmount(remainingBalance.toFixed(2));
      setTenderedAmount(remainingBalance.toFixed(2));
    } else if (method !== 'CASH') {
      setTenderedAmount(payAmount);
    }
  };

  const handlePay = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMsg('');

    if (numPayAmount <= 0) {
      setErrorMsg('Payment amount must be greater than $0.00');
      return;
    }
    if (numPayAmount > remainingBalance) {
      setErrorMsg(`Payment amount cannot exceed remaining balance of $${remainingBalance.toFixed(2)}`);
      return;
    }
    if (paymentMethod === 'CASH' && numTendered < numPayAmount) {
      setErrorMsg('Cash tendered must be at least equal to payment amount');
      return;
    }

    setIsSubmitting(true);

    try {
      // 1. Stripe Checkout Flow
      if (paymentMethod === 'STRIPE') {
        const returnUrl = `${window.location.origin}/billing/stripe-return?billId=${bill.id}&session_id={CHECKOUT_SESSION_ID}`;
        const cancelUrl = `${window.location.origin}/billing?billId=${bill.id}&status=cancelled`;

        const session = await createCheckoutSession(
          {
            billId: bill.id,
            gatewayProvider: 'STRIPE',
            notes: notes.trim() || undefined,
          },
          returnUrl,
          cancelUrl
        );

        if (session.checkoutUrl) {
          window.location.href = session.checkoutUrl;
          return;
        } else {
          throw new Error('Stripe checkout URL was not returned by server');
        }
      }

      // 2. Razorpay Flow
      if (paymentMethod === 'RAZORPAY') {
        const scriptLoaded = await loadRazorpayScript();
        if (!scriptLoaded) {
          throw new Error('Failed to load Razorpay payment SDK. Please check network connection.');
        }

        const session = await createCheckoutSession({
          billId: bill.id,
          gatewayProvider: 'RAZORPAY',
          notes: notes.trim() || undefined,
        });

        const RazorpayClass = (window as unknown as {
          Razorpay: new (options: Record<string, unknown>) => { open: () => void };
        }).Razorpay;

        const options = {
          key: session.keyId,
          amount: session.amountInSubunits,
          currency: session.currency,
          name: session.outletName || 'Restro POS',
          description: `Settlement for Bill #${session.billNumber}`,
          order_id: session.gatewayOrderId,
          prefill: {
            name: bill.customerName || undefined,
            contact: bill.customerPhone || undefined,
          },
          theme: {
            color: '#059669',
          },
          handler: async (resp: RazorpayResponse) => {
            try {
              setIsSubmitting(true);
              const updated = await verifyRazorpayPayment({
                billId: session.billId,
                paymentId: session.paymentId,
                razorpayOrderId: resp.razorpay_order_id,
                razorpayPaymentId: resp.razorpay_payment_id,
                razorpaySignature: resp.razorpay_signature,
              });
              onPaymentSuccess(updated);
            } catch (vErr: unknown) {
              const err = vErr as { response?: { data?: { message?: string } } };
              setErrorMsg(err?.response?.data?.message || 'Razorpay payment verification failed');
            } finally {
              setIsSubmitting(false);
            }
          },
          modal: {
            ondismiss: () => {
              setIsSubmitting(false);
            },
          },
        };

        const rzp = new RazorpayClass(options);
        rzp.open();
        return;
      }

      // 3. Manual In-Store Tender Flow (Cash, Card, UPI, Wallet)
      const updated = await processPayment(bill.id, {
        amount: numPayAmount,
        tenderedAmount: paymentMethod === 'CASH' ? numTendered : undefined,
        paymentMethod,
        transactionRef: transactionRef.trim() || undefined,
        notes: notes.trim() || undefined,
      });

      onPaymentSuccess(updated);
    } catch (err: unknown) {
      const error = err as { response?: { data?: { message?: string } }; message?: string };
      setErrorMsg(error?.response?.data?.message || error?.message || 'Failed to process payment');
      setIsSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4 overflow-y-auto">
      <div className="bg-white rounded-2xl shadow-2xl max-w-lg w-full overflow-hidden border border-slate-200 animate-in fade-in zoom-in-95 duration-150">
        {/* Modal Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-slate-200 bg-slate-50">
          <div>
            <h2 className="text-base font-black text-slate-800 flex items-center gap-2">
              <Coins className="w-5 h-5 text-emerald-600" />
              <span>Settle Payment • {bill.billNumber}</span>
            </h2>
            <p className="text-xs text-slate-500 mt-0.5">
              Order {bill.orderNumber} {bill.tableNumber ? `• Table ${bill.tableNumber}` : ''}
            </p>
          </div>
          <button
            onClick={onClose}
            className="p-1.5 rounded-lg text-slate-400 hover:text-slate-600 hover:bg-slate-200 transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Balance Overview Banner */}
        <div className="bg-slate-900 text-white px-6 py-4 flex items-center justify-between">
          <div>
            <p className="text-xs text-slate-400 uppercase tracking-wider font-semibold">Total Due</p>
            <p className="text-2xl font-black">${Number(bill.totalAmount).toFixed(2)}</p>
          </div>
          <div className="text-right">
            <p className="text-xs text-slate-400 uppercase tracking-wider font-semibold">Remaining Balance</p>
            <p className="text-2xl font-black text-emerald-400">${remainingBalance.toFixed(2)}</p>
          </div>
        </div>

        {/* Payment Form */}
        <form onSubmit={handlePay} className="p-6 space-y-5">
          {errorMsg && (
            <div className="p-3 rounded-xl bg-rose-50 border border-rose-200 text-rose-700 text-xs font-semibold flex items-center gap-2">
              <AlertCircle className="w-4 h-4 flex-shrink-0" />
              <span>{errorMsg}</span>
            </div>
          )}

          {/* Tender Method Selection */}
          <div className="space-y-3">
            <div>
              <span className="block text-[11px] font-bold text-slate-500 uppercase tracking-wider mb-1.5">
                In-Store / Manual Tender
              </span>
              <div className="grid grid-cols-4 gap-2">
                {[
                  { method: 'CASH' as PaymentMethod, label: 'Cash', icon: <Banknote className="w-4 h-4" /> },
                  { method: 'CARD' as PaymentMethod, label: 'Card Slip', icon: <CreditCard className="w-4 h-4" /> },
                  { method: 'UPI' as PaymentMethod, label: 'UPI Slip', icon: <QrCode className="w-4 h-4" /> },
                  { method: 'WALLET' as PaymentMethod, label: 'Wallet', icon: <Wallet className="w-4 h-4" /> },
                ].map(({ method, label, icon }) => {
                  const isSelected = paymentMethod === method;
                  return (
                    <button
                      key={method}
                      type="button"
                      onClick={() => handleMethodSelect(method)}
                      className={`flex flex-col items-center justify-center p-2.5 rounded-xl border text-xs font-bold transition-all ${
                        isSelected
                          ? 'bg-emerald-600 border-emerald-500 text-white shadow-md shadow-emerald-600/20 scale-[1.02]'
                          : 'bg-slate-50 border-slate-200 text-slate-700 hover:bg-slate-100'
                      }`}
                    >
                      {icon}
                      <span className="mt-1">{label}</span>
                    </button>
                  );
                })}
              </div>
            </div>

            <div>
              <span className="block text-[11px] font-bold text-slate-500 uppercase tracking-wider mb-1.5">
                Online Payment Gateways
              </span>
              <div className="grid grid-cols-2 gap-2">
                <button
                  type="button"
                  onClick={() => handleMethodSelect('STRIPE')}
                  className={`flex items-center justify-center gap-2 p-3 rounded-xl border text-xs font-bold transition-all ${
                    paymentMethod === 'STRIPE'
                      ? 'bg-indigo-600 border-indigo-500 text-white shadow-md shadow-indigo-600/20 scale-[1.02]'
                      : 'bg-slate-50 border-slate-200 text-slate-700 hover:bg-slate-100'
                  }`}
                >
                  <Globe className="w-4 h-4 text-indigo-400" />
                  <div className="text-left">
                    <span className="block font-bold">Stripe Checkout</span>
                    <span className="text-[10px] opacity-80 block font-normal">SGD / Cards / PayNow</span>
                  </div>
                </button>

                <button
                  type="button"
                  onClick={() => handleMethodSelect('RAZORPAY')}
                  className={`flex items-center justify-center gap-2 p-3 rounded-xl border text-xs font-bold transition-all ${
                    paymentMethod === 'RAZORPAY'
                      ? 'bg-blue-600 border-blue-500 text-white shadow-md shadow-blue-600/20 scale-[1.02]'
                      : 'bg-slate-50 border-slate-200 text-slate-700 hover:bg-slate-100'
                  }`}
                >
                  <Zap className="w-4 h-4 text-blue-300" />
                  <div className="text-left">
                    <span className="block font-bold">Razorpay Online</span>
                    <span className="text-[10px] opacity-80 block font-normal">INR / UPI / NetBanking</span>
                  </div>
                </button>
              </div>
            </div>
          </div>

          {/* Online Gateway Info Banner */}
          {paymentMethod === 'STRIPE' && (
            <div className="p-3.5 rounded-xl bg-indigo-50 border border-indigo-200 text-indigo-900 text-xs space-y-1">
              <div className="flex items-center gap-2 font-bold">
                <ShieldCheck className="w-4 h-4 text-indigo-600" />
                <span>Stripe-Hosted Checkout Session</span>
              </div>
              <p className="text-indigo-700 text-[11px] leading-relaxed">
                You will be redirected to the secure Stripe Checkout page to accept credit card, debit card, Apple Pay, or Singapore PayNow. The bill will automatically settle on webhook confirmation.
              </p>
            </div>
          )}

          {paymentMethod === 'RAZORPAY' && (
            <div className="p-3.5 rounded-xl bg-blue-50 border border-blue-200 text-blue-900 text-xs space-y-1">
              <div className="flex items-center gap-2 font-bold">
                <Zap className="w-4 h-4 text-blue-600" />
                <span>Razorpay Standard Checkout Modal</span>
              </div>
              <p className="text-blue-700 text-[11px] leading-relaxed">
                Opens the Razorpay checkout overlay with instant UPI QR, Cards, and NetBanking. Once the customer completes payment, cryptographic signature verification settles the bill immediately.
              </p>
            </div>
          )}

          {/* Amount to Pay (Split Support for In-Store, Locked for Online Gateways) */}
          <div className="space-y-1">
            <div className="flex items-center justify-between">
              <label className="text-xs font-bold text-slate-700 uppercase tracking-wider">
                Amount to Pay ($)
              </label>
              {!isOnlineGateway && (
                <button
                  type="button"
                  onClick={() => {
                    setPayAmount(remainingBalance.toFixed(2));
                    setTenderedAmount(remainingBalance.toFixed(2));
                  }}
                  className="text-xs text-emerald-600 hover:text-emerald-700 font-semibold"
                >
                  Pay Full Balance
                </button>
              )}
            </div>
            <input
              type="number"
              step="0.01"
              min="0.01"
              max={remainingBalance}
              value={payAmount}
              disabled={isOnlineGateway}
              onChange={(e) => {
                setPayAmount(e.target.value);
                if (paymentMethod !== 'CASH') {
                  setTenderedAmount(e.target.value);
                }
              }}
              className={`w-full px-3.5 py-2.5 rounded-xl border border-slate-300 text-base font-black text-slate-800 focus:outline-none focus:ring-2 focus:ring-emerald-500 ${
                isOnlineGateway ? 'bg-slate-100 cursor-not-allowed text-slate-500' : ''
              }`}
              required
            />
            {isOnlineGateway && (
              <p className="text-[11px] text-slate-500">
                Online gateway sessions are created for the full authoritative balance of ${remainingBalance.toFixed(2)}.
              </p>
            )}
          </div>

          {/* Cash Specific Details: Tendered & Quick Cash */}
          {paymentMethod === 'CASH' && (
            <div className="p-4 rounded-xl bg-slate-50 border border-slate-200 space-y-3">
              <div>
                <label className="block text-xs font-bold text-slate-600 uppercase tracking-wider mb-1">
                  Cash Tendered ($)
                </label>
                <input
                  type="number"
                  step="0.01"
                  min={numPayAmount}
                  value={tenderedAmount}
                  onChange={(e) => setTenderedAmount(e.target.value)}
                  className="w-full px-3 py-2 rounded-lg border border-slate-300 text-sm font-bold text-slate-800 focus:outline-none focus:ring-2 focus:ring-emerald-500"
                />
              </div>

              {/* Quick Cash Buttons */}
              <div className="flex items-center gap-1.5 flex-wrap">
                <span className="text-[11px] font-semibold text-slate-500 mr-1">Quick:</span>
                {[numPayAmount, 10, 20, 50, 100].map((val, idx) => {
                  if (val < numPayAmount && idx !== 0) return null;
                  return (
                    <button
                      key={idx}
                      type="button"
                      onClick={() => handleQuickCash(val)}
                      className="px-2.5 py-1 rounded-md bg-white border border-slate-300 text-xs font-bold text-slate-700 hover:bg-slate-100 hover:border-slate-400 transition-colors"
                    >
                      {idx === 0 ? 'Exact' : `$${val}`}
                    </button>
                  );
                })}
              </div>

              {/* Change Display */}
              <div className="pt-2 border-t border-slate-200 flex items-center justify-between text-xs">
                <span className="font-bold text-slate-600">Change Due:</span>
                <span className="text-base font-black text-emerald-600">
                  ${changeAmount.toFixed(2)}
                </span>
              </div>
            </div>
          )}

          {/* Manual Card / UPI Slip Reference Number */}
          {(paymentMethod === 'CARD' || paymentMethod === 'UPI' || paymentMethod === 'WALLET') && (
            <div>
              <label className="block text-xs font-bold text-slate-700 uppercase tracking-wider mb-1">
                {paymentMethod === 'CARD' ? 'Card Authorization / Slip No' : 'Transaction Reference / Slip'}
              </label>
              <input
                type="text"
                placeholder="e.g. AUTH-882310 or UPI-Ref-0091"
                value={transactionRef}
                onChange={(e) => setTransactionRef(e.target.value)}
                className="w-full px-3.5 py-2.5 rounded-xl border border-slate-300 text-sm text-slate-800 focus:outline-none focus:ring-2 focus:ring-emerald-500"
              />
            </div>
          )}

          {/* Notes input */}
          <div>
            <label className="block text-xs font-bold text-slate-700 uppercase tracking-wider mb-1">
              Payment Note (Optional)
            </label>
            <input
              type="text"
              placeholder="e.g. Split payment, customer coupon applied..."
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
              className="w-full px-3.5 py-2 rounded-xl border border-slate-300 text-xs text-slate-800 focus:outline-none focus:ring-2 focus:ring-emerald-500"
            />
          </div>

          {/* Modal Action Buttons */}
          <div className="flex items-center justify-end gap-3 pt-2">
            <Button type="button" variant="outline" onClick={onClose} disabled={isSubmitting}>
              Cancel
            </Button>
            <Button
              type="submit"
              variant="primary"
              disabled={isSubmitting || numPayAmount <= 0}
              className={`px-6 ${
                paymentMethod === 'STRIPE'
                  ? 'bg-indigo-600 hover:bg-indigo-700 text-white'
                  : paymentMethod === 'RAZORPAY'
                  ? 'bg-blue-600 hover:bg-blue-700 text-white'
                  : ''
              }`}
            >
              {isSubmitting ? (
                <span className="flex items-center gap-2">
                  <Loader2 className="w-4 h-4 animate-spin" />
                  Processing...
                </span>
              ) : paymentMethod === 'STRIPE' ? (
                <span className="flex items-center gap-1.5">
                  <ExternalLink className="w-4 h-4" />
                  Launch Stripe Checkout (${remainingBalance.toFixed(2)})
                </span>
              ) : paymentMethod === 'RAZORPAY' ? (
                <span className="flex items-center gap-1.5">
                  <Zap className="w-4 h-4" />
                  Pay with Razorpay (${remainingBalance.toFixed(2)})
                </span>
              ) : (
                <span className="flex items-center gap-1.5">
                  <CheckCircle2 className="w-4 h-4" />
                  Confirm &amp; Settle ${numPayAmount.toFixed(2)}
                </span>
              )}
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
};
