import { ROLES } from '../types/auth';
import type { Role, SignupPayload } from '../types/auth';
import { PRIORITIES } from '../types/domain';

/** Field name -> message. Empty object = valid. Limits mirror the backend DTO annotations. */
export type FieldErrors = Record<string, string>;

export const hasErrors = (errors: FieldErrors): boolean => Object.keys(errors).length > 0;

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

function required(value: string, label: string, max: number): string | null {
  const v = value.trim();
  if (!v) return `${label} is required`;
  if (v.length > max) return `${label} must be at most ${max} characters`;
  return null;
}
function optionalMax(value: string, label: string, max: number): string | null {
  return value.trim().length > max ? `${label} must be at most ${max} characters` : null;
}
function put(errors: FieldErrors, key: string, message: string | null) {
  if (message) errors[key] = message;
}

/** Empty string -> null, otherwise trimmed. Used when building request bodies. */
export const blankToNull = (value: string): string | null => (value.trim() === '' ? null : value.trim());

// ---- Customer ----
export interface CustomerForm {
  companyName: string;
  contactEmail: string;
}
export function validateCustomer(f: CustomerForm): FieldErrors {
  const e: FieldErrors = {};
  put(e, 'companyName', required(f.companyName, 'Company name', 150));
  const email = f.contactEmail.trim();
  if (email) {
    if (email.length > 150) e.contactEmail = 'Contact email must be at most 150 characters';
    else if (!EMAIL_RE.test(email)) e.contactEmail = 'Enter a valid email address';
  }
  return e;
}

// ---- Site ----
export interface SiteForm {
  /** Only used when staff create a site (the portal derives the customer from the login). */
  customerId?: string;
  name: string;
  addressLine: string;
  city: string;
}
export function validateSite(f: SiteForm, needsCustomer: boolean): FieldErrors {
  const e: FieldErrors = {};
  if (needsCustomer && !f.customerId) e.customerId = 'Choose a customer';
  put(e, 'name', required(f.name, 'Site name', 150));
  put(e, 'addressLine', optionalMax(f.addressLine, 'Address', 255));
  put(e, 'city', optionalMax(f.city, 'City', 100));
  return e;
}

// ---- Work order ----
export interface WorkOrderForm {
  siteId: string;
  title: string;
  description: string;
  priority: string;
}
export function validateWorkOrder(f: WorkOrderForm): FieldErrors {
  const e: FieldErrors = {};
  if (!f.siteId) e.siteId = 'Choose a site';
  put(e, 'title', required(f.title, 'Title', 200));
  put(e, 'description', optionalMax(f.description, 'Description', 2000));
  if (!(PRIORITIES as readonly string[]).includes(f.priority)) e.priority = 'Choose a priority';
  return e;
}

// ---- Part ----
export interface PartForm {
  sku: string;
  name: string;
  description: string;
  unit: string;
  quantityOnHand: string;
  reorderLevel: string;
  unitCost: string;
}
function validateWholeNumber(raw: string, label: string, min: number, max: number): string | null {
  const v = raw.trim();
  if (v === '') return `${label} is required`;
  if (!/^\d+$/.test(v)) return `${label} must be a whole number`;
  const n = Number(v);
  if (n < min) return `${label} must be ${min} or greater`;
  if (n > max) return `${label} is too large`;
  return null;
}
export function validatePart(f: PartForm): FieldErrors {
  const e: FieldErrors = {};
  put(e, 'sku', required(f.sku, 'SKU', 50));
  put(e, 'name', required(f.name, 'Name', 150));
  put(e, 'description', optionalMax(f.description, 'Description', 500));
  put(e, 'unit', required(f.unit, 'Unit', 20));
  put(e, 'quantityOnHand', validateWholeNumber(f.quantityOnHand, 'Quantity on hand', 0, 100_000_000));
  put(e, 'reorderLevel', validateWholeNumber(f.reorderLevel, 'Reorder level', 0, 100_000_000));
  const cost = f.unitCost.trim();
  if (cost === '') e.unitCost = 'Unit cost is required';
  else if (!/^\d+(\.\d{1,2})?$/.test(cost)) e.unitCost = 'Unit cost must be a number with at most 2 decimals';
  else if (Number(cost) > 9_999_999_999.99) e.unitCost = 'Unit cost is too large';
  return e;
}

// ---- Part usage (technician) ----
export interface PartUsageForm {
  partId: string;
  quantity: string;
  note: string;
}
/** `available` = stock on hand of the chosen part, when known (catches the obvious error early). */
export function validatePartUsage(f: PartUsageForm, available?: number | null): FieldErrors {
  const e: FieldErrors = {};
  if (!f.partId) e.partId = 'Choose a part';
  const q = f.quantity.trim();
  if (q === '') e.quantity = 'Quantity is required';
  else if (!/^\d+$/.test(q)) e.quantity = 'Quantity must be a whole number';
  else if (Number(q) < 1) e.quantity = 'Quantity must be at least 1';
  else if (Number(q) > 1_000_000) e.quantity = 'Quantity is too large';
  else if (available !== undefined && available !== null && Number(q) > available) {
    e.quantity = `Only ${available} in stock`;
  }
  put(e, 'note', optionalMax(f.note, 'Note', 500));
  return e;
}

// ---- Time log (technician) ----
export interface TimeLogForm {
  startedAt: string;
  endedAt: string;
  note: string;
}
/** startedAt/endedAt are datetime-local strings. `now` is injectable for tests. */
export function validateTimeLog(f: TimeLogForm, now: Date = new Date()): FieldErrors {
  const e: FieldErrors = {};
  const start = f.startedAt ? new Date(f.startedAt) : null;
  const end = f.endedAt ? new Date(f.endedAt) : null;
  if (!start || Number.isNaN(start.getTime())) e.startedAt = 'Start time is required';
  if (!end || Number.isNaN(end.getTime())) e.endedAt = 'End time is required';
  if (!e.startedAt && !e.endedAt && start && end) {
    const minutes = (end.getTime() - start.getTime()) / 60000;
    if (minutes <= 0) e.endedAt = 'End time must be after the start time';
    else if (minutes < 1) e.endedAt = 'A time log must be at least 1 minute long';
    else if (minutes > 24 * 60) e.endedAt = 'A single time log cannot exceed 24 hours';
    else if (end.getTime() > now.getTime() + 60_000) e.endedAt = 'End time cannot be in the future';
  }
  put(e, 'note', optionalMax(f.note, 'Note', 500));
  return e;
}

// ---- Free-text note on a status change ----
export function validateNote(note: string): string | null {
  return optionalMax(note, 'Note', 500);
}

// ---- Sign in / Sign up ----
export interface LoginForm {
  email: string;
  password: string;
}
/** Sign in only checks presence: the backend decides whether the credentials are right. */
export function validateLogin(f: LoginForm): FieldErrors {
  const e: FieldErrors = {};
  if (!f.email.trim()) e.email = 'Email is required';
  if (!f.password) e.password = 'Password is required';
  return e;
}

export interface SignupForm {
  fullName: string;
  email: string;
  password: string;
  confirmPassword: string;
  role: string;
  companyName: string;
}
/** Backend: @Size(min = 6) on password; BCrypt only uses the first 72 bytes. */
export const PASSWORD_MIN = 6;
export const PASSWORD_MAX_BYTES = 72;

const byteLength = (v: string): number => new TextEncoder().encode(v).length;

/** Limits mirror SignupRequest + the app_users / customers column lengths. */
export function validateSignup(f: SignupForm): FieldErrors {
  const e: FieldErrors = {};
  put(e, 'fullName', required(f.fullName, 'Full name', 100));

  const email = f.email.trim();
  if (!email) e.email = 'Email is required';
  else if (email.length > 150) e.email = 'Email must be at most 150 characters';
  else if (!EMAIL_RE.test(email)) e.email = 'Enter a valid email address';

  if (!f.password) e.password = 'Password is required';
  else if (f.password.length < PASSWORD_MIN) e.password = `Password must be at least ${PASSWORD_MIN} characters`;
  else if (byteLength(f.password) > PASSWORD_MAX_BYTES) e.password = `Password is too long (max ${PASSWORD_MAX_BYTES} bytes)`;

  if (!f.confirmPassword) e.confirmPassword = 'Confirm your password';
  else if (f.confirmPassword !== f.password) e.confirmPassword = 'Passwords do not match';

  if (!(ROLES as readonly string[]).includes(f.role)) e.role = 'Choose a role';
  else if (f.role === 'CUSTOMER') put(e, 'companyName', required(f.companyName, 'Company name', 150));

  return e;
}

/** Request body for POST /api/auth/signup; companyName is only sent for CUSTOMER. */
export function buildSignupPayload(f: SignupForm): SignupPayload {
  const payload: SignupPayload = {
    fullName: f.fullName.trim(),
    email: f.email.trim(),
    password: f.password,
    role: f.role as Role,
  };
  if (f.role === 'CUSTOMER') payload.companyName = f.companyName.trim();
  return payload;
}

const SIGNUP_FIELDS = ['fullName', 'email', 'password', 'role', 'companyName'];

/**
 * Turn a backend signup error into field errors + an optional banner.
 *  - 400 validation arrives as "field: message"  -> shown under that field
 *  - 409 "An account with this email already exists" -> under Email
 *  - 409 "Company name is required ..." -> under Company name
 *  - anything else -> banner only
 */
export function mapSignupError(message: string): { fieldErrors: FieldErrors; banner: string | null } {
  const m = /^(\w+):\s*(.+)$/.exec(message);
  if (m && SIGNUP_FIELDS.includes(m[1])) return { fieldErrors: { [m[1]]: m[2] }, banner: null };
  if (/email already exists/i.test(message)) return { fieldErrors: { email: message }, banner: message };
  if (/company name/i.test(message)) return { fieldErrors: { companyName: message }, banner: null };
  return { fieldErrors: {}, banner: message };
}
