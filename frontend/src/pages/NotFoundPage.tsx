import { Link } from 'react-router-dom';
import FullPageMessage from '../components/FullPageMessage';

export default function NotFoundPage() {
  return (
    <FullPageMessage text="Page not found.">
      <Link to="/">Back to start</Link>
    </FullPageMessage>
  );
}
