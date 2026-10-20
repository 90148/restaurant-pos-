import apiClient from './client';
import {
  Bill,
  BillingStats,
  ProcessPaymentRequest,
  BillFilterParams,
  CreateCheckoutSessionRequest,
  CreateCustomerCheckoutSessionRequest,
  CheckoutSessionResponse,
  RazorpayVerifyPaymentRequest,
} from '@/types/billing';
import { ApiResponse, PagedResponse } from '@/types/api';

export const generateBill = async (orderId: string): Promise<Bill> => {
  const response = await apiClient.post<ApiResponse<Bill>>(`/bills/order/${orderId}`);
  return response.data.data;
};

export const getBillById = async (id: string): Promise<Bill> => {
  const response = await apiClient.get<ApiResponse<Bill>>(`/bills/${id}`);
  return response.data.data;
};

export const getBillByOrderId = async (orderId: string): Promise<Bill> => {
  const response = await apiClient.get<ApiResponse<Bill>>(`/bills/order/${orderId}`);
  return response.data.data;
};

export const getBills = async (params: BillFilterParams): Promise<PagedResponse<Bill>> => {
  const response = await apiClient.get<ApiResponse<PagedResponse<Bill>>>('/bills', { params });
  return response.data.data;
};

export const getBillingStats = async (outletId: string): Promise<BillingStats> => {
  const response = await apiClient.get<ApiResponse<BillingStats>>('/bills/stats', {
    params: { outletId },
  });
  return response.data.data;
};

export const processPayment = async (
  billId: string,
  data: ProcessPaymentRequest
): Promise<Bill> => {
  const response = await apiClient.post<ApiResponse<Bill>>(`/bills/${billId}/payments`, data);
  return response.data.data;
};

export const createCheckoutSession = async (
  data: CreateCheckoutSessionRequest,
  returnUrl?: string,
  cancelUrl?: string
): Promise<CheckoutSessionResponse> => {
  const params: Record<string, string> = {};
  if (returnUrl) params.returnUrl = returnUrl;
  if (cancelUrl) params.cancelUrl = cancelUrl;

  const response = await apiClient.post<ApiResponse<CheckoutSessionResponse>>(
    '/payments/checkout-session',
    data,
    { params }
  );
  return response.data.data;
};

export const verifyRazorpayPayment = async (
  data: RazorpayVerifyPaymentRequest
): Promise<Bill> => {
  const response = await apiClient.post<ApiResponse<Bill>>(
    '/payments/razorpay/verify',
    data
  );
  return response.data.data;
};

export const verifyStripePayment = async (
  sessionId: string,
  billId?: string
): Promise<Bill> => {
  const params: Record<string, string> = { sessionId };
  if (billId) params.billId = billId;
  const response = await apiClient.post<ApiResponse<Bill>>(
    '/payments/stripe/verify',
    null,
    { params }
  );
  return response.data.data;
};

export const createCustomerCheckoutSession = async (
  data: CreateCustomerCheckoutSessionRequest
): Promise<CheckoutSessionResponse> => {
  const response = await apiClient.post<ApiResponse<CheckoutSessionResponse>>(
    '/payments/customer/checkout-session',
    data
  );
  return response.data.data;
};
