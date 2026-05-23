import clsx, { type ClassValue } from 'clsx';

/** classnames helper — used everywhere instead of string concatenation */
export function cn(...inputs: ClassValue[]): string {
  return clsx(inputs);
}
