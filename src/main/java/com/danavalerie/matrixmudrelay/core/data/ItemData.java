package com.danavalerie.matrixmudrelay.core.data;

public class ItemData {
    private String itemName;
    private String description;
    private String appraiseText;
    private String weight;
    private double dollarValue;
    private int searchable;
    private String specialFindNote;

    public ItemData() {}

    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getAppraiseText() { return appraiseText; }
    public void setAppraiseText(String appraiseText) { this.appraiseText = appraiseText; }

    public String getWeight() { return weight; }
    public void setWeight(String weight) { this.weight = weight; }

    public double getDollarValue() { return dollarValue; }
    public void setDollarValue(double dollarValue) { this.dollarValue = dollarValue; }

    public int getSearchable() { return searchable; }
    public void setSearchable(int searchable) { this.searchable = searchable; }

    public String getSpecialFindNote() { return specialFindNote; }
    public void setSpecialFindNote(String specialFindNote) { this.specialFindNote = specialFindNote; }
}
