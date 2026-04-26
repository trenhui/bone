
import React from "react";
import { BlockProps, defaultBlockProps } from "./props";
import PKInput from "../Input";
import PKSelectDrop from "../SelectDrop";

const componentRegistry = {
  PKInput,
  PKSelectDrop,
};

const PKBlock: React.FC<BlockProps> = (props) => {
  const mergedProps = { ...defaultBlockProps, ...props };

  const renderComponent = (schemaNode: any): React.ReactNode => {
    if (!schemaNode) return null;

    const Component = componentRegistry[schemaNode.type as keyof typeof componentRegistry];
    
    if (!Component) {
      console.warn(`组件 ${schemaNode.type} 未找到`);
      return null;
    }

    return <Component key={schemaNode.id} {...schemaNode} />;
  };

  return (
    <div className="p-4">
      {mergedProps.body?.map((item: any, index: number) => (
        <div 
          key={item.id} 
          className={index < mergedProps.body.length - 1 ? "mb-1" : ""}
        >
          {renderComponent(item)}
        </div>
      ))}
    </div>
  );
};

export default PKBlock;
