import type { ReactNode } from 'react';

export default function FullPageMessage({ text, children }: { text: string; children?: ReactNode }) {
  return (
    <div className="center-screen">
      <p>{text}</p>
      {children}
    </div>
  );
}
