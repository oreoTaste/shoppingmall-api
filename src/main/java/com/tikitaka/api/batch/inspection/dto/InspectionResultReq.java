package com.tikitaka.api.batch.inspection.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class InspectionResultReq {

    private String yyyymmdd;
    private String status;
    private String inspectionStatus;
    private String lgroup;
    private String mgroup;
    private String sgroup;
    private String dgroup;
    private String forbiddenWord;
    private String goodsCode;
    private String goodsName;
    private String mobileGoodsName;
    
    @Override
    public String toString() {
        return "InspectionResultReq {" +
                "yyyymmdd='" + yyyymmdd + '\'' +
                ", status='" + status + '\'' +
                ", inspectionStatus='" + inspectionStatus + '\'' +
                ", lgroup='" + lgroup + '\'' +
                ", mgroup='" + mgroup + '\'' +
                ", sgroup='" + sgroup + '\'' +
                ", dgroup='" + dgroup + '\'' +
                ", forbiddenWord='" + forbiddenWord + '\'' +
                ", goodsCode='" + goodsCode + '\'' +
                ", goodsName='" + goodsName + '\'' +
                ", mobileGoodsName='" + mobileGoodsName + '\'' +
                '}';
    }
}
