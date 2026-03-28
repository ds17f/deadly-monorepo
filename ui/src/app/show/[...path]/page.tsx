import ShowRedirectClient from "./ShowRedirectClient";

export function generateStaticParams() {
  return [{ path: ["_"] }];
}

export default function ShowRedirect() {
  return <ShowRedirectClient />;
}
