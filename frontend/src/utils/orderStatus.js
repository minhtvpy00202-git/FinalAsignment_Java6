const ORDER_STATUS_LABELS = {
    PENDING_PAYMENT: "Đang chờ thanh toán",
    PLACED_PAID: "Đã đặt - đã TT",
    PLACED_UNPAID: "Đã đặt - chưa TT",
    NEW: "Đã đặt - chưa TT",
    PLACED: "Đã đặt - chưa TT",
    SHIPPING_PAID: "Đang giao - đã TT",
    SHIPPING_UNPAID: "Đang giao - chưa TT",
    SHIPPING: "Đang giao - chưa TT",
    DONE: "Giao thành công",
    DELIVERED_SUCCESS: "Giao thành công",
    CANCEL: "Giao thất bại",
    DELIVERY_FAILED: "Giao thất bại",
    REFUND_REQUEST: "Chờ xử lý hoàn tiền",
    SUCCESS: "Đã chấp nhận hoàn tiền",
    DECLINED: "Đã từ chối hoàn tiền",
    DECLINE: "Đã từ chối hoàn tiền"
};

export const orderStatusLabel = (status) => {
    const key = String(status || "").toUpperCase();
    return ORDER_STATUS_LABELS[key] || status || "Không rõ";
};

export const refundStatusLabel = (status) => {
    const key = String(status || "").toUpperCase();
    if (key === "PENDING") return "Chờ xử lý";
    if (key === "SUCCESS") return "Đã chấp nhận";
    if (key === "DECLINED" || key === "DECLINE") return "Đã từ chối";
    return key || "Chờ xử lý";
};
