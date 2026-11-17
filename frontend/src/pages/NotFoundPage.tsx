import { Link } from 'react-router-dom';
import { Compass } from 'lucide-react';
import { buttonVariants } from '@/components/ui/button';
import { EmptyState } from '@/components/common/States';

export default function NotFoundPage() {
  return (
    <EmptyState icon={<Compass />} title="Page not found" description="The page you were looking for doesn't exist or has moved."
      action={<Link to="/" className={buttonVariants({ variant: 'secondary' })}>Back to overview</Link>} />
  );
}
