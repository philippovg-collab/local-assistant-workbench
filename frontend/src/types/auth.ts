export type AuthSession = {
  authenticated: boolean;
  username: string | null;
  roles: string[];
  csrfHeaderName?: string | null;
  csrfToken?: string | null;
};

export type AuthLoginRequest = {
  username: string;
  password: string;
};
