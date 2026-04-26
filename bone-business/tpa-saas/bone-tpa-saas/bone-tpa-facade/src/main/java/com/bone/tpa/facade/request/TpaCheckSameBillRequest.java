package com.bone.tpa.facade.request;

import lombok.Data;

@Data
public class TpaCheckSameBillRequest {

    /**
     * 赔案号
     */
        public String claimNumber;

    /**
     * 发票号
     */
        public String invoiceNo;
        /**
         * 查重开关
         */
        private boolean chachongCheck=true;

        /**
         * 疑似开关
         */
        private boolean yisiCheck =true;
        /**
         * 特殊开关
         */
        private boolean teshuCheck =true;
}
