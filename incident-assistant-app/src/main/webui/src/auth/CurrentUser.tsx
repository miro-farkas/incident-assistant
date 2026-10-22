import { createContext, useContext } from "react";
import { APPROVER_ROLE, type CurrentUser } from "../api/types";

export const CurrentUserContext = createContext<CurrentUser | null>(null);

export function useCurrentUser(): CurrentUser {
  const user = useContext(CurrentUserContext);
  if (user === null) {
    throw new Error("useCurrentUser needs a CurrentUserContext");
  }
  return user;
}

/** Only for showing or hiding controls; the API checks the role on every request. */
export function isApprover(user: CurrentUser): boolean {
  return user.roles?.includes(APPROVER_ROLE) ?? false;
}
