package com.bone.tpa.push.service.impl;

import com.bone.tpa.push.enums.TreatmentTypeEnum;
import com.bone.tpa.push.service.VisitTreatmentTypeStrategy;
import org.springframework.stereotype.Component;

@Component
public class VisitTreatmentTypeStrategyFactory {

    private final HospitalizedVisitTreatmentTypeStrategy hospitalizedStrategy;
    private final OutpatientVisitTreatmentTypeStrategy outpatientVisitTreatmentTypeStrategy;

    public VisitTreatmentTypeStrategyFactory(HospitalizedVisitTreatmentTypeStrategy hospitalizedStrategy,
                                             OutpatientVisitTreatmentTypeStrategy outpatientVisitTreatmentTypeStrategy) {
        this.hospitalizedStrategy = hospitalizedStrategy;
        this.outpatientVisitTreatmentTypeStrategy = outpatientVisitTreatmentTypeStrategy;
    }

    public VisitTreatmentTypeStrategy getStrategy(String treatmentType) {
        if (TreatmentTypeEnum.zy.getValue().toString().equals(treatmentType)) {
            return hospitalizedStrategy;
        }
        return outpatientVisitTreatmentTypeStrategy;
    }
}
