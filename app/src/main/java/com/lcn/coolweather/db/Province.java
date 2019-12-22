package com.lcn.coolweather.db;

import org.litepal.crud.DataSupport;

public class Province extends DataSupport {
    private int id;
    private String provinecName;
    private int provinceCode;

    public void setId(int id) {
        this.id = id;
    }

    public void setProvinecName(String provinecName) {
        this.provinecName = provinecName;
    }

    public void setProvinceCode(int provinceCode) {
        this.provinceCode = provinceCode;
    }

    public int getId() {
        return id;
    }

    public String getProvinecName() {
        return provinecName;
    }

    public int getProvinceCode() {
        return provinceCode;
    }
}
