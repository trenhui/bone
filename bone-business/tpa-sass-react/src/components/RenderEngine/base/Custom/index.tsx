import React from 'react';

// Components
import RelateLiability from './RelateLiability';
import RelateLiabilityView from './RelateLiability/view';

interface CustomProps {
  code: string;
  isCellView?: boolean;
  [key: string]: any;
}

interface CustomEvents {
  onChange?: (value: any) => void;
  onBlur?: (event: any) => void;
}

const CustomComponent: React.FC<CustomProps & CustomEvents> = ({
  code,
  isCellView = false,
  onChange,
  onBlur,
  ...restProps
}) => {
  const getComponentType = () => {
    if (code === 'relateLiability') {
      return isCellView ? RelateLiabilityView : RelateLiability;
    }
    return null;
  };

  const Component = getComponentType();

  if (!Component) {
    return null;
  }

  return (
    <Component
      {...restProps}
      onChange={onChange}
      onBlur={onBlur}
    />
  );
};

export default CustomComponent;