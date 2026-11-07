// @krizaka/auth ships ESM only and Jest resolves CommonJS: suites that load the proxy get this stand-in for the cookie rules
// (the package covers them in its own tests; proxy.test.ts replaces it with a spy to check what the proxy hands it).
export const sessionHint = (response: Response): Response => response;
