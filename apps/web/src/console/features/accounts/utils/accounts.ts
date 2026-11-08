/** The words of the identity service's refusals, by their stable code. Pure, tested. */
export function refusalMessage(code: string | null, status: number): string {
  switch (code) {
    case "SELF":
      return "You cannot do this to your own account.";
    case "LAST_ADMIN":
      return "The last administrator keeps the role.";
    case "CONFIRM_MISMATCH":
      return "The e-mail typed does not match the account.";
    default:
      if (status === 403) return "Only an administrator can do this.";
      if (status === 404) return "This account no longer exists.";
      return "The identity service refused the change.";
  }
}

/** The action's label for an account: the one that undoes its current state. */
export function stateActions(user: { enabled: boolean; admin: boolean }): {
  status: "suspend" | "restore";
  role: "grant" | "withdraw";
} {
  return { status: user.enabled ? "suspend" : "restore", role: user.admin ? "withdraw" : "grant" };
}

/** Whether the typed e-mail arms the erasure (case and spaces do not matter). */
export function erasureArmed(typed: string, email: string): boolean {
  return typed.trim().toLowerCase() === email.trim().toLowerCase();
}
