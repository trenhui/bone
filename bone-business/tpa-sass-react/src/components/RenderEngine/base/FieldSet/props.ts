export interface FieldSetProps {
  id?: string;
  name?: string;
  body?: any[];
  children?: React.ReactNode;
}

export const defaultFieldSetProps: Partial<FieldSetProps> = {
  id: "",
  name: "",
  body: [],
};
