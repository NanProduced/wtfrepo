import "next-auth";
import "next-auth/jwt";

declare module "next-auth" {
  interface Session {
    backendAccessToken?: string;
    user?: {
      userId?: string;
      username?: string;
      usernameChanged?: boolean;
      roles?: string[];
      bugBalance?: number;
      backendAccessToken?: string;
      name?: string | null;
      email?: string | null;
      image?: string | null;
    };
  }
}

declare module "next-auth/jwt" {
  interface JWT {
    backendAccessToken?: string;
    user?: {
      userId?: string;
      username?: string;
      usernameChanged?: boolean;
      roles?: string[];
      bugBalance?: number;
    };
  }
}
