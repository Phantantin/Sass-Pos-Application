package com.tindev.domain;

/**
 * Nguồn gốc của một thay đổi tồn kho. Các giá trị này là dữ liệu audit nên
 * không được đổi tên sau khi đã được lưu trong cơ sở dữ liệu.
 */
public enum InventoryMovementType {
    INITIAL_STOCK,
    ADJUSTMENT,
    SALE,
    REFUND,
    REMOVAL,
    TRANSFER_OUT,
    TRANSFER_IN
}
