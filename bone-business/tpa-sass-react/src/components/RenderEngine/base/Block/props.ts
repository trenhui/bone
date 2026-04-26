
export interface BlockProps {
  id?: string;
  name?: string;
  body?: any[];
}

export const defaultBlockProps: Partial<BlockProps> = {
  id: "",
  name: "",
  body: [],
};
