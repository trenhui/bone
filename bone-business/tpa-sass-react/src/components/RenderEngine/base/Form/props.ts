
export interface FormProps {
  id?: string;
  eventTriggerList?: any[];
  body?: any[];
  isAffix?: boolean;
}

export const defaultFormProps: Partial<FormProps> = {
  id: "",
  eventTriggerList: [],
  body: [],
  isAffix: false,
};
