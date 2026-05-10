import { apiClient } from "./client";
import { setClientEventReporter, type ClientEventInput } from "@/utils/clientEvents";

let installed = false;

export const installApiClientEventReporter = () => {
  if (installed) {
    return;
  }
  installed = true;

  setClientEventReporter((event: ClientEventInput) => apiClient.logClientEvent(event));
};
