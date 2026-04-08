import {onMounted, reactive, ref} from "vue";
import {api} from "@/api";

const money = (value) => {
    if (value === null || value === undefined || value === "") {
        return "0";
    }
    return Number(value).toLocaleString("vi-VN");
};

const dateTime = (value) => value ? new Date(value).toLocaleString("vi-VN") : "";

const productCard = (product) => `
    <div class="card h-100 shadow-sm">
      <a href="/product/detail?id=${product.id || ""}" style="text-decoration:none;color:inherit">
        <img class="card-img-top" style="height:180px;object-fit:cover" src="${product.image ? `/images/${product.image}` : "/images/logo.png"}" alt="${product.name || ""}">
      </a>
      <div class="card-body">
        <h6><a href="/product/detail?id=${product.id || ""}" style="text-decoration:none;color:inherit">${product.name || "Sản phẩm"}</a></h6>
        <div class="text-danger fw-bold">${money(product.price)} đ</div>
      </div>
    </div>
`;

const HomePage = {
    setup() {
        const filter = reactive({keyword: "", categoryId: "", page: 0, size: 12, sort: ""});
        const data = ref(null);
        const loading = ref(false);
        const error = ref("");
        const load = async () => {
            loading.value = true;
            error.value = "";
            try {
                const res = await api.home.index(filter);
                data.value = res.data || {};
            } catch (e) {
                error.value = e.message;
            } finally {
                loading.value = false;
            }
        };
        onMounted(load);
        return {filter, data, loading, error, load, productCard};
    },
    template: `
      <div>
        <h3 class="mb-3">Trang chủ</h3>
        <div class="card shadow-sm mb-3">
          <div class="card-body">
            <div class="row g-2">
              <div class="col-md-4"><input v-model="filter.keyword" class="form-control" placeholder="Từ khóa"></div>
              <div class="col-md-3"><input v-model="filter.categoryId" class="form-control" placeholder="Mã danh mục"></div>
              <div class="col-md-2"><select v-model="filter.sort" class="form-select"><option value="">Mặc định</option><option value="priceAsc">Giá tăng</option><option value="priceDesc">Giá giảm</option></select></div>
              <div class="col-md-1"><input v-model.number="filter.page" type="number" min="0" class="form-control"></div>
              <div class="col-md-1"><input v-model.number="filter.size" type="number" min="1" class="form-control"></div>
              <div class="col-md-1"><button class="btn btn-primary w-100" @click="load">Lọc</button></div>
            </div>
          </div>
        </div>
        <div v-if="loading" class="alert alert-info">Đang tải...</div>
        <div v-if="error" class="alert alert-danger">{{ error }}</div>
        <template v-if="data">
          <div class="row g-3 mb-3">
            <div class="col-md-4"><div class="card"><div class="card-body"><div class="text-muted">Tổng danh mục</div><h4>{{ (data.categories || []).length }}</h4></div></div></div>
            <div class="col-md-4"><div class="card"><div class="card-body"><div class="text-muted">Trang hiện tại</div><h4>{{ data.currentPage || 0 }}</h4></div></div></div>
            <div class="col-md-4"><div class="card"><div class="card-body"><div class="text-muted">Tổng trang</div><h4>{{ data.totalPages || 0 }}</h4></div></div></div>
          </div>
          <div class="card shadow-sm mb-3" v-if="(data.filteredProducts || []).length">
            <div class="card-header">Kết quả lọc</div>
            <div class="card-body">
              <div class="row g-3">
                <div class="col-md-3" v-for="p in data.filteredProducts" :key="'f'+p.id" v-html="productCard(p)"></div>
              </div>
            </div>
          </div>
          <div class="card shadow-sm mb-3">
            <div class="card-header">Sản phẩm mới</div>
            <div class="card-body"><div class="row g-3"><div class="col-md-3" v-for="p in (data.newProducts||[])" :key="'n'+p.id" v-html="productCard(p)"></div></div></div>
          </div>
          <div class="card shadow-sm mb-3">
            <div class="card-header">Giảm giá cao</div>
            <div class="card-body"><div class="row g-3"><div class="col-md-3" v-for="p in (data.discountProducts||[])" :key="'d'+p.id" v-html="productCard(p)"></div></div></div>
          </div>
          <div class="card shadow-sm">
            <div class="card-header">Bán chạy</div>
            <div class="card-body"><div class="row g-3"><div class="col-md-3" v-for="p in (data.bestSellerProducts||[])" :key="'b'+p.id" v-html="productCard(p)"></div></div></div>
          </div>
        </template>
      </div>
    `
};

const LoginPage = {
    setup() {
        const form = reactive({username: "", password: ""});
        const me = ref(null);
        const loading = ref(false);
        const error = ref("");
        const login = async () => {
            loading.value = true;
            error.value = "";
            try {
                await api.auth.login(form.username, form.password);
                me.value = (await api.auth.me()).data;
            } catch (e) {
                error.value = e.message === "Unauthorized" ? "Sai tài khoản hoặc mật khẩu" : e.message;
            } finally {
                loading.value = false;
            }
        };
        const loadMe = async () => {
            try {
                me.value = (await api.auth.me()).data;
            } catch (e) {
                if (e.message === "Unauthorized") {
                    me.value = null;
                    error.value = "";
                    return;
                }
                error.value = e.message;
            }
        };
        const logout = async () => {
            try {
                await api.auth.logout();
            } catch (e) {
            } finally {
                me.value = null;
            }
        };
        return {form, me, loading, error, login, loadMe, logout};
    },
    template: `
      <div class="row justify-content-center">
        <div class="col-md-6">
          <div class="card shadow-sm">
            <div class="card-header">Đăng nhập</div>
            <div class="card-body">
              <div class="mb-3"><label class="form-label">Username</label><input v-model="form.username" class="form-control"></div>
              <div class="mb-3"><label class="form-label">Password</label><input type="password" v-model="form.password" class="form-control"></div>
              <div class="d-flex gap-2">
                <button class="btn btn-primary" :disabled="loading" @click="login">Đăng nhập</button>
                <button class="btn btn-outline-secondary" @click="loadMe">Kiểm tra phiên</button>
                <button class="btn btn-outline-danger" @click="logout">Đăng xuất</button>
              </div>
            </div>
          </div>
          <div v-if="error" class="alert alert-danger mt-3">{{ error }}</div>
          <div v-if="me" class="card mt-3"><div class="card-body"><div><strong>User:</strong> {{ me.username }}</div><div><strong>Roles:</strong> {{ (me.roles || []).join(', ') }}</div></div></div>
        </div>
      </div>
    `
};

const ProductListPage = {
    setup() {
        const filter = reactive({keyword: "", categoryId: "", minPrice: "", maxPrice: "", sort: "", page: 0, size: 12});
        const data = ref({});
        const loading = ref(false);
        const error = ref("");
        const load = async () => {
            loading.value = true;
            error.value = "";
            try {
                const res = await api.products.list(filter);
                data.value = res.data || {};
            } catch (e) {
                error.value = e.message;
            } finally {
                loading.value = false;
            }
        };
        const next = () => {
            const total = data.value.totalPages || 1;
            if (filter.page + 1 < total) {
                filter.page += 1;
                load();
            }
        };
        const prev = () => {
            if (filter.page > 0) {
                filter.page -= 1;
                load();
            }
        };
        onMounted(load);
        return {filter, data, loading, error, load, next, prev, productCard};
    },
    template: `
      <div>
        <h3>Danh sách sản phẩm</h3>
        <div class="card shadow-sm mb-3">
          <div class="card-body">
            <div class="row g-2">
              <div class="col-md-4"><input v-model="filter.keyword" class="form-control" placeholder="Từ khóa"></div>
              <div class="col-md-3"><input v-model="filter.categoryId" class="form-control" placeholder="Danh mục"></div>
              <div class="col-md-2"><input v-model.number="filter.size" type="number" min="1" class="form-control"></div>
              <div class="col-md-3 d-flex gap-2">
                <button class="btn btn-primary" @click="load">Tìm</button>
                <button class="btn btn-outline-secondary" @click="prev">Trang trước</button>
                <button class="btn btn-outline-secondary" @click="next">Trang sau</button>
              </div>
            </div>
          </div>
        </div>
        <div v-if="loading" class="alert alert-info">Đang tải...</div>
        <div v-if="error" class="alert alert-danger">{{ error }}</div>
        <div class="row g-3">
          <div class="col-md-3" v-for="p in (data.products || [])" :key="p.id" v-html="productCard(p)"></div>
        </div>
        <div class="mt-3 text-muted">Trang {{ data.currentPage || 0 }} / {{ data.totalPages || 0 }}</div>
      </div>
    `
};

const ProductDetailPage = {
    setup() {
        const productId = ref(1);
        const data = ref(null);
        const error = ref("");
        const load = async () => {
            try {
                data.value = (await api.products.detail(productId.value)).data;
            } catch (e) {
                error.value = e.message;
            }
        };
        return {productId, data, error, load, money};
    },
    template: `
      <div>
        <h3>Chi tiết sản phẩm</h3>
        <div class="row g-2 mb-3">
          <div class="col-md-3"><input v-model.number="productId" type="number" class="form-control"></div>
          <div class="col-md-2"><button class="btn btn-primary w-100" @click="load">Tải</button></div>
        </div>
        <div v-if="error" class="alert alert-danger">{{ error }}</div>
        <div class="card" v-if="data && data.product">
          <div class="card-body">
            <div class="row">
              <div class="col-md-4"><img class="img-fluid rounded" :src="data.product.image ? '/images/' + data.product.image : '/images/logo.png'"></div>
              <div class="col-md-8">
                <h4>{{ data.product.name }}</h4>
                <div class="text-danger fs-5">{{ money(data.product.price) }} đ</div>
                <div class="mt-2">{{ data.product.description }}</div>
                <hr>
                <div><strong>Đánh giá trung bình:</strong> {{ data.avgRatingValue || 0 }} / 5</div>
              </div>
            </div>
          </div>
        </div>
      </div>
    `
};

const CartPage = {
    setup() {
        const state = reactive({items: [], totalPrice: 0});
        const error = ref("");
        const loading = ref(false);
        const load = async () => {
            loading.value = true;
            try {
                const res = await api.cart.get();
                state.items = res.data?.items || [];
                state.totalPrice = res.data?.totalPrice || 0;
            } catch (e) {
                error.value = e.message;
            } finally {
                loading.value = false;
            }
        };
        const updateItem = async (item) => {
            await api.cart.update(item.productId, item.sizeId, item.quantity);
            await load();
        };
        const removeItem = async (item) => {
            await api.cart.remove(item.productId, item.sizeId);
            await load();
        };
        const clear = async () => {
            try {
                await api.cart.clear();
            } catch (e) {
                const snapshot = [...state.items];
                for (const item of snapshot) {
                    await api.cart.remove(item.productId, item.sizeId);
                }
            } finally {
                await load();
            }
        };
        onMounted(load);
        return {state, error, loading, load, updateItem, removeItem, clear, money};
    },
    template: `
      <div>
        <h3>Giỏ hàng</h3>
        <div v-if="loading" class="alert alert-info">Đang tải...</div>
        <div v-if="error" class="alert alert-danger">{{ error }}</div>
        <div class="card shadow-sm">
          <div class="card-body p-0">
            <table class="table mb-0">
              <thead><tr><th>Sản phẩm</th><th>Size</th><th>Giá</th><th>SL</th><th>Thành tiền</th><th></th></tr></thead>
              <tbody>
                <tr v-for="item in state.items" :key="item.productId + '-' + item.sizeId">
                  <td>{{ item.name }}</td>
                  <td>{{ item.sizeName }}</td>
                  <td>{{ money(item.price) }}</td>
                  <td style="width:120px"><input type="number" min="1" v-model.number="item.quantity" class="form-control form-control-sm"></td>
                  <td>{{ money((item.price || 0) * (item.quantity || 0)) }}</td>
                  <td class="text-end">
                    <button class="btn btn-sm btn-outline-primary me-2" @click="updateItem(item)">Lưu</button>
                    <button class="btn btn-sm btn-outline-danger" @click="removeItem(item)">Xóa</button>
                  </td>
                </tr>
                <tr v-if="!state.items.length"><td colspan="6" class="text-center text-muted py-3">Giỏ hàng trống</td></tr>
              </tbody>
            </table>
          </div>
        </div>
        <div class="d-flex justify-content-between align-items-center mt-3">
          <div class="fs-5">Tổng tiền: <strong>{{ money(state.totalPrice) }} đ</strong></div>
          <div class="d-flex gap-2">
            <router-link class="btn btn-success" to="/order/check-out">Thanh toán</router-link>
            <button class="btn btn-outline-danger" @click="clear">Xóa giỏ hàng</button>
          </div>
        </div>
      </div>
    `
};

const CheckoutPage = {
    setup() {
        const checkout = ref({items: [], totalPrice: 0});
        const form = reactive({
            address: "",
            addressDetail: "",
            provinceCode: "",
            wardCode: "",
            lat: "",
            lng: "",
            shippingPhone: "",
            paymentMethod: "BANK"
        });
        const result = ref(null);
        const error = ref("");
        const load = async () => {
            checkout.value = (await api.orderWorkflow.checkoutData()).data || {items: [], totalPrice: 0};
            if (!form.shippingPhone) {
                form.shippingPhone = checkout.value?.shippingPhone || "";
            }
        };
        const submit = async () => {
            try {
                result.value = await api.orderWorkflow.checkout(form);
            } catch (e) {
                error.value = e.message;
            }
        };
        onMounted(load);
        return {checkout, form, result, error, submit, money};
    },
    template: `
      <div>
        <h3>Thanh toán</h3>
        <div class="row g-3">
          <div class="col-md-7">
            <div class="card shadow-sm"><div class="card-body p-0">
              <table class="table mb-0">
                <thead><tr><th>Sản phẩm</th><th>Size</th><th>SL</th><th>Đơn giá</th></tr></thead>
                <tbody><tr v-for="item in checkout.items" :key="item.productId + '-' + item.sizeId"><td>{{ item.name }}</td><td>{{ item.sizeName }}</td><td>{{ item.quantity }}</td><td>{{ money(item.price) }}</td></tr></tbody>
              </table>
            </div></div>
            <div class="mt-2 fw-bold">Tổng tiền: {{ money(checkout.totalPrice) }} đ</div>
          </div>
          <div class="col-md-5">
            <div class="card shadow-sm">
              <div class="card-body">
                <div class="mb-2"><label class="form-label">Địa chỉ</label><input v-model="form.address" class="form-control"></div>
                <div class="row g-2 mb-2"><div class="col"><label class="form-label">Lat</label><input v-model="form.lat" class="form-control"></div><div class="col"><label class="form-label">Lng</label><input v-model="form.lng" class="form-control"></div></div>
                <div class="mb-3"><label class="form-label">Thanh toán</label><select v-model="form.paymentMethod" class="form-select"><option value="BANK">BANK</option><option value="COD">COD</option></select></div>
                <button class="btn btn-primary w-100" @click="submit">Đặt hàng</button>
              </div>
            </div>
          </div>
        </div>
        <div v-if="error" class="alert alert-danger mt-3">{{ error }}</div>
        <div v-if="result" class="alert alert-success mt-3">Đặt hàng thành công. Mã đơn: {{ result.data?.orderId }}</div>
      </div>
    `
};

const BankTransferPage = {
    setup() {
        const orderId = ref("");
        const data = ref(null);
        const error = ref("");
        const load = async () => {
            try {
                data.value = (await api.orderWorkflow.bankTransfer(orderId.value)).data;
            } catch (e) {
                error.value = e.message;
            }
        };
        const confirm = async () => {
            await api.orderWorkflow.confirmBankTransfer(orderId.value);
            await load();
        };
        const toCod = async () => {
            await api.orderWorkflow.switchToCod(orderId.value);
            await load();
        };
        const remove = async () => {
            await api.orderWorkflow.cancelAndDelete(orderId.value);
            data.value = null;
        };
        return {orderId, data, error, load, confirm, toCod, remove, money};
    },
    template: `
      <div>
        <h3>Chuyển khoản ngân hàng</h3>
        <div class="row g-2 mb-3">
          <div class="col-md-3"><input v-model="orderId" class="form-control" placeholder="orderId"></div>
          <div class="col-md-2"><button class="btn btn-primary w-100" @click="load">Tải</button></div>
        </div>
        <div v-if="error" class="alert alert-danger">{{ error }}</div>
        <div v-if="data" class="card shadow-sm">
          <div class="card-body">
            <div class="row">
              <div class="col-md-6">
                <div><strong>Đơn:</strong> #{{ data.order?.id }}</div>
                <div><strong>Số tiền:</strong> {{ money(data.totalPrice) }} đ</div>
                <div><strong>Ngân hàng:</strong> {{ data.bankName }}</div>
                <div><strong>Số TK:</strong> {{ data.accountNumber }}</div>
              </div>
              <div class="col-md-6 text-end">
                <img v-if="data.qrImageSrc" :src="data.qrImageSrc" style="max-width:200px">
              </div>
            </div>
            <div class="d-flex gap-2 mt-3">
              <a class="btn btn-success" :href="data.checkoutUrl" target="_blank">Mở link thanh toán</a>
              <button class="btn btn-outline-primary" @click="confirm">Xác nhận đã thanh toán</button>
              <button class="btn btn-outline-warning" @click="toCod">Chuyển COD</button>
              <button class="btn btn-outline-danger" @click="remove">Hủy và xóa đơn</button>
            </div>
          </div>
        </div>
      </div>
    `
};

const OrderListPage = {
    setup() {
        const orders = ref([]);
        const error = ref("");
        const load = async () => {
            try {
                orders.value = (await api.orderWorkflow.orderList()).data || [];
            } catch (e) {
                error.value = e.message;
            }
        };
        onMounted(load);
        return {orders, error, load, dateTime};
    },
    template: `
      <div>
        <h3>Đơn hàng của tôi</h3>
        <button class="btn btn-primary mb-3" @click="load">Tải danh sách</button>
        <div v-if="error" class="alert alert-danger">{{ error }}</div>
        <div class="card shadow-sm"><div class="card-body p-0">
          <table class="table mb-0">
            <thead><tr><th>Mã đơn</th><th>Địa chỉ</th><th>Trạng thái</th><th>Ngày tạo</th><th></th></tr></thead>
            <tbody>
              <tr v-for="o in orders" :key="o.id">
                <td>#{{ o.id }}</td><td>{{ o.address }}</td><td><span class="badge text-bg-secondary">{{ o.status }}</span></td><td>{{ dateTime(o.createDate) }}</td>
                <td class="text-end"><router-link class="btn btn-sm btn-outline-primary" :to="'/order/order-detail?id=' + o.id">Chi tiết</router-link></td>
              </tr>
            </tbody>
          </table>
        </div></div>
      </div>
    `
};

const OrderDetailPage = {
    setup() {
        const orderId = ref("");
        const data = ref(null);
        const payos = ref(null);
        const error = ref("");
        const load = async () => {
            try {
                data.value = (await api.orderWorkflow.orderDetail(orderId.value)).data;
            } catch (e) {
                error.value = e.message;
            }
        };
        const checkPayos = async () => {
            payos.value = (await api.orderWorkflow.payosStatus(orderId.value)).data;
            await load();
        };
        return {orderId, data, payos, error, load, checkPayos, money};
    },
    template: `
      <div>
        <h3>Chi tiết đơn hàng</h3>
        <div class="row g-2 mb-3">
          <div class="col-md-3"><input v-model="orderId" class="form-control" placeholder="orderId"></div>
          <div class="col-md-2"><button class="btn btn-primary w-100" @click="load">Tải</button></div>
          <div class="col-md-2"><button class="btn btn-outline-secondary w-100" @click="checkPayos">Kiểm tra PayOS</button></div>
        </div>
        <div v-if="error" class="alert alert-danger">{{ error }}</div>
        <div class="card shadow-sm" v-if="data">
          <div class="card-body">
            <div class="mb-2"><strong>Mã đơn:</strong> #{{ data.order?.id }} | <strong>Trạng thái:</strong> {{ data.order?.status }}</div>
            <table class="table">
              <thead><tr><th>Sản phẩm</th><th>Size</th><th>SL</th><th>Giá</th></tr></thead>
              <tbody><tr v-for="d in (data.details||[])" :key="d.id"><td>{{ d.product?.name || d.productName || d.product?.id }}</td><td>{{ d.sizeName }}</td><td>{{ d.quantity }}</td><td>{{ money(d.price) }}</td></tr></tbody>
            </table>
            <div v-if="payos" class="alert alert-info">Trạng thái cổng thanh toán: {{ payos.status || payos.message }}</div>
          </div>
        </div>
      </div>
    `
};

const MyProductListPage = {
    setup() {
        const rows = ref([]);
        const error = ref("");
        const load = async () => {
            try {
                rows.value = (await api.orderWorkflow.myProducts()).data || [];
            } catch (e) {
                error.value = e.message;
            }
        };
        onMounted(load);
        return {rows, error, load, money};
    },
    template: `
      <div>
        <h3>Sản phẩm đã mua</h3>
        <button class="btn btn-primary mb-3" @click="load">Tải dữ liệu</button>
        <div v-if="error" class="alert alert-danger">{{ error }}</div>
        <div class="card shadow-sm"><div class="card-body p-0">
          <table class="table mb-0">
            <thead><tr><th>Mã đơn</th><th>Sản phẩm</th><th>Size</th><th>Số lượng</th><th>Giá</th></tr></thead>
            <tbody><tr v-for="r in rows" :key="r.id"><td>#{{ r.order?.id }}</td><td>{{ r.product?.name }}</td><td>{{ r.sizeName }}</td><td>{{ r.quantity }}</td><td>{{ money(r.price) }}</td></tr></tbody>
          </table>
        </div></div>
      </div>
    `
};

const SignUpPage = {
    setup() {
        const form = reactive({username: "", password: "", fullname: "", email: ""});
        const message = ref("");
        const submit = async () => {
            try {
                await api.account.signUp(form);
                message.value = "Đăng ký thành công";
            } catch (e) {
                message.value = e.message;
            }
        };
        return {form, message, submit};
    },
    template: `
      <div class="row justify-content-center">
        <div class="col-md-7">
          <div class="card shadow-sm"><div class="card-header">Đăng ký tài khoản</div><div class="card-body">
            <div class="row g-2">
              <div class="col-md-6"><input v-model="form.username" class="form-control" placeholder="Username"></div>
              <div class="col-md-6"><input type="password" v-model="form.password" class="form-control" placeholder="Password"></div>
              <div class="col-md-6"><input v-model="form.fullname" class="form-control" placeholder="Họ tên"></div>
              <div class="col-md-6"><input v-model="form.email" class="form-control" placeholder="Email"></div>
            </div>
            <button class="btn btn-primary mt-3" @click="submit">Đăng ký</button>
            <div v-if="message" class="alert alert-info mt-3 mb-0">{{ message }}</div>
          </div></div>
        </div>
      </div>
    `
};

const EditProfilePage = {
    setup() {
        const form = reactive({fullname: "", email: "", phone: "", address: "", photo: "", photoFile: null});
        const message = ref("");
        const onPhotoChange = (event) => {
            form.photoFile = event?.target?.files?.[0] || null;
        };
        const load = async () => {
            const me = (await api.account.profile()).data || {};
            form.fullname = me.fullname || "";
            form.email = me.email || "";
            form.phone = me.phone || "";
            form.address = me.address || "";
            form.photo = me.photo || "";
            form.photoFile = null;
        };
        const save = async () => {
            try {
                await api.account.updateProfile(form);
                message.value = "Cập nhật thành công";
                await load();
            } catch (e) {
                message.value = e.message;
            }
        };
        onMounted(load);
        return {form, message, save, onPhotoChange};
    },
    template: `
      <div class="row justify-content-center"><div class="col-md-7"><div class="card shadow-sm"><div class="card-header">Hồ sơ cá nhân</div><div class="card-body">
        <div class="mb-2"><label class="form-label">Họ tên</label><input v-model="form.fullname" class="form-control"></div>
        <div class="mb-2"><label class="form-label">Email</label><input v-model="form.email" class="form-control"></div>
        <button class="btn btn-primary" @click="save">Lưu thay đổi</button>
        <div v-if="message" class="alert alert-info mt-3 mb-0">{{ message }}</div>
      </div></div></div></div>
    `
};

const ChangePasswordPage = {
    setup() {
        const form = reactive({currentPassword: "", newPassword: ""});
        const message = ref("");
        const submit = async () => {
            try {
                await api.account.changePassword(form.currentPassword, form.newPassword);
                message.value = "Đổi mật khẩu thành công";
            } catch (e) {
                message.value = e.message;
            }
        };
        return {form, message, submit};
    },
    template: `
      <div class="row justify-content-center"><div class="col-md-6"><div class="card shadow-sm"><div class="card-header">Đổi mật khẩu</div><div class="card-body">
        <div class="mb-2"><input type="password" v-model="form.currentPassword" class="form-control" placeholder="Mật khẩu hiện tại"></div>
        <div class="mb-2"><input type="password" v-model="form.newPassword" class="form-control" placeholder="Mật khẩu mới"></div>
        <button class="btn btn-primary" @click="submit">Cập nhật</button>
        <div v-if="message" class="alert alert-info mt-3 mb-0">{{ message }}</div>
      </div></div></div></div>
    `
};

const ForgotPasswordPage = {
    setup() {
        const email = ref("");
        const message = ref("");
        const submit = async () => {
            try {
                const res = await api.account.forgotPassword(email.value);
                message.value = `Mật khẩu mới: ${res.data?.newPassword || ""}`;
            } catch (e) {
                message.value = e.message;
            }
        };
        return {email, message, submit};
    },
    template: `
      <div class="row justify-content-center"><div class="col-md-6"><div class="card shadow-sm"><div class="card-header">Quên mật khẩu</div><div class="card-body">
        <div class="input-group">
          <input v-model="email" class="form-control" placeholder="Nhập email">
          <button class="btn btn-primary" @click="submit">Đặt lại</button>
        </div>
        <div v-if="message" class="alert alert-info mt-3 mb-0">{{ message }}</div>
      </div></div></div></div>
    `
};

const AdminAccountPage = {
    setup() {
        const rows = ref([]);
        const roles = ref([]);
        const form = reactive({
            username: "",
            password: "",
            fullname: "",
            email: "",
            phone: "",
            address: "",
            roleId: "USER",
            activated: true,
            photo: "",
            photoFile: null
        });
        const modalOpen = ref(false);
        const editing = ref(false);
        const msg = ref("");
        const isValidPhone = (phone) => /^(0|\+84)\d{9,10}$/.test((phone || "").trim());
        const load = async () => {
            const res = await api.admin.accounts.list();
            rows.value = res.data?.accounts || [];
            roles.value = res.data?.roles || [];
        };
        const edit = async (username) => {
            const res = await api.admin.accounts.detail(username);
            const data = res.data || {};
            form.username = data.account?.username || username;
            form.fullname = data.account?.fullname || "";
            form.email = data.account?.email || "";
            form.phone = data.account?.phone || "";
            form.address = data.account?.address || "";
            form.photo = data.account?.photo || "";
            form.photoFile = null;
            form.password = "";
            form.activated = data.account?.activated ?? true;
            form.roleId = data.roleId || "USER";
            editing.value = true;
            modalOpen.value = true;
        };
        const openCreate = () => {
            reset();
            editing.value = false;
            modalOpen.value = true;
        };
        const reset = () => {
            form.username = "";
            form.password = "";
            form.fullname = "";
            form.email = "";
            form.phone = "";
            form.address = "";
            form.roleId = "USER";
            form.activated = true;
            form.photo = "";
            form.photoFile = null;
            editing.value = false;
        };
        const closeModal = () => {
            modalOpen.value = false;
            reset();
        };
        const onPhotoChange = (event) => {
            form.photoFile = event?.target?.files?.[0] || null;
        };
        const save = async () => {
            try {
                if (!form.phone?.trim()) {
                    msg.value = "Số điện thoại là bắt buộc";
                    return;
                }
                if (!isValidPhone(form.phone)) {
                    msg.value = "Số điện thoại không hợp lệ";
                    return;
                }
                if (!form.address?.trim()) {
                    msg.value = "Địa chỉ là bắt buộc";
                    return;
                }
                if (editing.value) {
                    await api.admin.accounts.update(form.username, form);
                    msg.value = "Cập nhật tài khoản thành công";
                } else {
                    await api.admin.accounts.create(form);
                    msg.value = "Tạo tài khoản thành công";
                }
                await load();
                closeModal();
            } catch (e) {
                msg.value = e.message;
            }
        };
        const remove = async (username) => {
            await api.admin.accounts.remove(username);
            await load();
        };
        onMounted(load);
        return {rows, roles, form, modalOpen, editing, msg, edit, openCreate, closeModal, onPhotoChange, save, remove};
    },
    template: `
      <div>
        <h3>Quản lý tài khoản</h3>
        <div class="card shadow-sm mb-3">
          <div class="card-header">{{ editing ? 'Cập nhật tài khoản' : 'Tạo tài khoản' }}</div>
          <div class="card-body">
            <div class="row g-2">
              <div class="col-md-2"><input v-model="form.username" :disabled="editing" class="form-control" placeholder="Username"></div>
              <div class="col-md-2"><input v-model="form.password" type="password" class="form-control" placeholder="Password"></div>
              <div class="col-md-3"><input v-model="form.fullname" class="form-control" placeholder="Họ tên"></div>
              <div class="col-md-3"><input v-model="form.email" class="form-control" placeholder="Email"></div>
              <div class="col-md-2">
                <select v-model="form.roleId" class="form-select">
                  <option v-for="r in roles" :key="r.id" :value="r.id">{{ r.id }}</option>
                </select>
              </div>
            </div>
            <div class="form-check mt-2"><input class="form-check-input" type="checkbox" v-model="form.activated" id="active"><label class="form-check-label" for="active">Kích hoạt</label></div>
            <div class="d-flex gap-2 mt-3"><button class="btn btn-primary" @click="save">Lưu</button><button class="btn btn-outline-secondary" @click="reset">Làm mới</button></div>
            <div v-if="msg" class="alert alert-info mt-3 mb-0">{{ msg }}</div>
          </div>
        </div>
        <div class="card shadow-sm"><div class="card-body p-0">
          <table class="table mb-0"><thead><tr><th>Username</th><th>Họ tên</th><th>Email</th><th>Active</th><th></th></tr></thead>
            <tbody><tr v-for="u in rows" :key="u.username"><td>{{ u.username }}</td><td>{{ u.fullname }}</td><td>{{ u.email }}</td><td>{{ u.activated }}</td><td class="text-end"><button class="btn btn-sm btn-outline-primary me-2" @click="edit(u.username)">Sửa</button><button class="btn btn-sm btn-outline-danger" @click="remove(u.username)">Xóa</button></td></tr></tbody>
          </table>
        </div></div>
      </div>
    `
};

const AdminCategoryPage = {
    setup() {
        const rows = ref([]);
        const modal = reactive({show: false, id: "", name: "", editing: false});
        const load = async () => {
            rows.value = (await api.admin.categories.list()).data || [];
        };
        const openCreate = () => {
            modal.show = true;
            modal.id = "";
            modal.name = "";
            modal.editing = false;
        };
        const openEdit = async (id) => {
            const detail = (await api.admin.categories.detail(id)).data || {};
            modal.show = true;
            modal.id = detail.id || id;
            modal.name = detail.name || "";
            modal.editing = true;
        };
        const save = async () => {
            if (modal.editing) {
                await api.admin.categories.update(modal.id, modal.name);
            } else {
                await api.admin.categories.create(modal.id, modal.name);
            }
            modal.show = false;
            await load();
        };
        const remove = async (id) => {
            await api.admin.categories.remove(id);
            await load();
        };
        onMounted(load);
        return {rows, modal, load, openCreate, openEdit, save, remove};
    },
    template: `
      <div>
        <div class="d-flex justify-content-between align-items-center mb-3"><h3 class="mb-0">Quản lý danh mục</h3><button class="btn btn-primary" @click="openCreate">Thêm danh mục</button></div>
        <div class="card shadow-sm"><div class="card-body p-0">
          <table class="table mb-0"><thead><tr><th>Mã</th><th>Tên danh mục</th><th></th></tr></thead>
            <tbody><tr v-for="c in rows" :key="c.id"><td>{{ c.id }}</td><td>{{ c.name }}</td><td class="text-end"><button class="btn btn-sm btn-outline-primary me-2" @click="openEdit(c.id)">Sửa</button><button class="btn btn-sm btn-outline-danger" @click="remove(c.id)">Xóa</button></td></tr></tbody>
          </table>
        </div></div>
        <div v-if="modal.show" class="modal-backdrop-custom">
          <div class="modal-content-custom">
            <h5>{{ modal.editing ? 'Cập nhật danh mục' : 'Thêm danh mục' }}</h5>
            <div class="mb-2"><label class="form-label">Mã</label><input v-model="modal.id" class="form-control" :disabled="modal.editing"></div>
            <div class="mb-3"><label class="form-label">Tên</label><input v-model="modal.name" class="form-control"></div>
            <div class="d-flex gap-2 justify-content-end"><button class="btn btn-secondary" @click="modal.show=false">Hủy</button><button class="btn btn-primary" @click="save">Lưu</button></div>
          </div>
        </div>
      </div>
    `
};

const AdminProductPage = {
    setup() {
        const state = reactive({
            rows: [],
            categories: [],
            sizes: [],
            page: 0,
            totalPages: 0,
            keyword: "",
            categoryId: "",
            minPrice: "",
            maxPrice: ""
        });
        const form = reactive({
            id: "",
            name: "",
            price: "",
            discount: "",
            quantity: "",
            image: "",
            imageFile: null,
            categoryId: "",
            description: "",
            sizeQtyMap: {}
        });
        const editing = ref(false);
        const message = ref("");
        const load = async () => {
            try {
                const res = await api.admin.products.list({
                    page: state.page,
                    keyword: state.keyword,
                    categoryId: state.categoryId,
                    minPrice: state.minPrice,
                    maxPrice: state.maxPrice
                });
                const data = res.data || {};
                state.rows = data.products || [];
                state.categories = data.categories || [];
                state.sizes = data.sizes || [];
                state.totalPages = data.totalPages || 0;
                message.value = "";
            } catch (e) {
                state.rows = [];
                message.value = e.message;
            }
        };
        const edit = async (id) => {
            const res = await api.admin.products.detail(id, {page: state.page});
            const p = res.data?.product || {};
            form.id = p.id;
            form.name = p.name || "";
            form.price = p.price || "";
            form.discount = p.discount || "";
            form.quantity = p.quantity || "";
            form.image = p.image || "";
            form.imageFile = null;
            form.categoryId = p.category?.id || "";
            form.description = p.description || "";
            form.sizeQtyMap = {};
            const map = res.data?.sizeQtyMap || {};
            Object.keys(map).forEach((key) => {
                form.sizeQtyMap[key] = Number(map[key] || 0);
            });
            editing.value = true;
        };
        const reset = () => {
            form.id = "";
            form.name = "";
            form.price = "";
            form.discount = "";
            form.quantity = "";
            form.image = "";
            form.imageFile = null;
            form.categoryId = "";
            form.description = "";
            form.sizeQtyMap = {};
            editing.value = false;
        };
        const save = async () => {
            const payload = {
                name: form.name,
                price: form.price,
                discount: form.discount,
                categoryId: form.categoryId,
                description: form.description
            };
            if (form.imageFile) {
                payload.imageFile = form.imageFile;
            }
            let totalQty = 0;
            state.sizes.forEach((size) => {
                const key = String(size.id);
                const raw = form.sizeQtyMap?.[key];
                const qty = Number.isFinite(Number(raw)) ? Math.max(0, Number(raw)) : 0;
                payload[`size_${size.id}`] = String(qty);
                totalQty += qty;
            });
            payload.quantity = totalQty;
            form.quantity = totalQty;
            if (editing.value) {
                await api.admin.products.update(form.id, payload);
                message.value = "Cập nhật sản phẩm thành công";
            } else {
                await api.admin.products.create(payload);
                message.value = "Tạo sản phẩm thành công";
            }
            await load();
            reset();
        };
        const remove = async (id) => {
            await api.admin.products.remove(id);
            await load();
        };
        const next = async () => {
            if (state.page + 1 < state.totalPages) {
                state.page += 1;
                await load();
            }
        };
        const prev = async () => {
            if (state.page > 0) {
                state.page -= 1;
                await load();
            }
        };
        onMounted(load);
        return {state, form, editing, message, load, edit, reset, save, remove, next, prev, money};
    },
    template: `
      <div>
        <h3>Quản lý sản phẩm</h3>
        <div class="card shadow-sm mb-3"><div class="card-body">
          <div class="row g-2 mb-2">
            <div class="col-md-4"><input v-model="state.keyword" class="form-control" placeholder="Tìm kiếm"></div>
            <div class="col-md-3"><select v-model="state.categoryId" class="form-select"><option value="">Tất cả danh mục</option><option v-for="c in state.categories" :key="c.id" :value="c.id">{{ c.name }}</option></select></div>
            <div class="col-md-5 d-flex gap-2"><button class="btn btn-primary" @click="load">Lọc</button><button class="btn btn-outline-secondary" @click="prev">Trang trước</button><button class="btn btn-outline-secondary" @click="next">Trang sau</button></div>
          </div>
          <div class="text-muted">Trang {{ state.page }} / {{ state.totalPages || 0 }}</div>
        </div></div>
        <div class="card shadow-sm mb-3"><div class="card-header">{{ editing ? 'Cập nhật sản phẩm' : 'Thêm sản phẩm' }}</div><div class="card-body">
          <div class="row g-2">
            <div class="col-md-4"><input v-model="form.name" class="form-control" placeholder="Tên sản phẩm"></div>
            <div class="col-md-2"><input v-model="form.price" type="number" class="form-control" placeholder="Giá"></div>
            <div class="col-md-2"><input v-model="form.discount" type="number" class="form-control" placeholder="Giảm giá"></div>
            <div class="col-md-2"><input v-model="form.quantity" type="number" class="form-control" placeholder="Số lượng"></div>
            <div class="col-md-2"><select v-model="form.categoryId" class="form-select"><option value="">Danh mục</option><option v-for="c in state.categories" :key="'f'+c.id" :value="c.id">{{ c.name }}</option></select></div>
            <div class="col-md-12"><textarea v-model="form.description" class="form-control" rows="2" placeholder="Mô tả"></textarea></div>
          </div>
          <div class="form-check mt-2"><input class="form-check-input" type="checkbox" v-model="form.available" id="av"><label class="form-check-label" for="av">Đang kinh doanh</label></div>
          <div class="d-flex gap-2 mt-3"><button class="btn btn-primary" @click="save">Lưu</button><button class="btn btn-outline-secondary" @click="reset">Làm mới</button></div>
          <div v-if="message" class="alert alert-info mt-3 mb-0">{{ message }}</div>
        </div></div>
        <div class="card shadow-sm"><div class="card-body p-0">
          <table class="table mb-0"><thead><tr><th>ID</th><th>Tên</th><th>Giá</th><th>SL</th><th>Danh mục</th><th></th></tr></thead>
            <tbody><tr v-for="p in state.rows" :key="p.id"><td>{{ p.id }}</td><td>{{ p.name }}</td><td>{{ money(p.price) }}</td><td>{{ p.quantity }}</td><td>{{ p.category?.name }}</td><td class="text-end"><button class="btn btn-sm btn-outline-primary me-2" @click="edit(p.id)">Sửa</button><button class="btn btn-sm btn-outline-danger" @click="remove(p.id)">Xóa</button></td></tr></tbody>
          </table>
        </div></div>
      </div>
    `
};

const AdminOrderPage = {
    setup() {
        const rows = ref([]);
        const selected = ref(null);
        const status = ref("");
        const payosCode = ref("");
        const msg = ref("");
        const load = async () => {
            rows.value = (await api.admin.orders.list()).data || [];
        };
        const detail = async (id) => {
            selected.value = (await api.admin.orders.detail(id)).data;
            status.value = selected.value?.order?.status || "";
            payosCode.value = selected.value?.order?.id || "";
        };
        const updateStatus = async () => {
            await api.admin.orders.updateStatus(selected.value.order.id, status.value);
            msg.value = "Cập nhật trạng thái thành công";
            await detail(selected.value.order.id);
            await load();
        };
        const cancelPayos = async () => {
            const res = await api.admin.orders.cancelPayos(payosCode.value);
            msg.value = res.message;
        };
        const remove = async (id) => {
            await api.admin.orders.remove(id);
            if (selected.value?.order?.id === id) {
                selected.value = null;
            }
            await load();
        };
        onMounted(load);
        return {rows, selected, status, payosCode, msg, load, detail, updateStatus, cancelPayos, remove, dateTime};
    },
    template: `
      <div>
        <h3>Quản lý đơn hàng</h3>
        <div class="row g-3">
          <div class="col-md-7">
            <div class="card shadow-sm"><div class="card-body p-0">
              <table class="table mb-0"><thead><tr><th>Mã đơn</th><th>Khách</th><th>Trạng thái</th><th>Ngày</th><th></th></tr></thead>
              <tbody><tr v-for="o in rows" :key="o.id"><td>#{{ o.id }}</td><td>{{ o.account?.username }}</td><td>{{ o.status }}</td><td>{{ dateTime(o.createDate) }}</td><td class="text-end"><button class="btn btn-sm btn-outline-primary me-2" @click="detail(o.id)">Xem</button><button class="btn btn-sm btn-outline-danger" @click="remove(o.id)">Xóa</button></td></tr></tbody>
              </table>
            </div></div>
          </div>
          <div class="col-md-5">
            <div class="card shadow-sm"><div class="card-header">Chi tiết đơn</div><div class="card-body" v-if="selected">
              <div class="mb-2"><strong>Đơn #{{ selected.order.id }}</strong></div>
              <div class="mb-2">
                <label class="form-label">Trạng thái</label>
                <input v-model="status" class="form-control">
              </div>
              <div class="d-grid mb-3"><button class="btn btn-primary" @click="updateStatus">Cập nhật trạng thái</button></div>
              <div class="mb-2"><label class="form-label">Mã đơn PayOS</label><input v-model="payosCode" class="form-control"></div>
              <div class="d-grid"><button class="btn btn-outline-warning" @click="cancelPayos">Hủy PayOS pending</button></div>
            </div><div class="card-body text-muted" v-else>Chọn đơn hàng bên trái để thao tác.</div></div>
            <div v-if="msg" class="alert alert-info mt-3">{{ msg }}</div>
          </div>
        </div>
      </div>
    `
};

const AdminRevenuePage = {
    setup() {
        const params = reactive({fromDate: "", toDate: "", sortField: "orderId", sortDir: "asc"});
        const rows = ref([]);
        const total = ref(0);
        const load = async () => {
            const data = (await api.admin.reports.revenue(params)).data || {};
            rows.value = data.rows || [];
            total.value = data.grandTotal || 0;
        };
        onMounted(load);
        return {params, rows, total, load, money};
    },
    template: `
      <div>
        <h3>Báo cáo doanh thu</h3>
        <div class="card shadow-sm mb-3"><div class="card-body">
          <div class="row g-2">
            <div class="col-md-3"><input v-model="params.fromDate" class="form-control" placeholder="Từ ngày YYYY-MM-DD"></div>
            <div class="col-md-3"><input v-model="params.toDate" class="form-control" placeholder="Đến ngày YYYY-MM-DD"></div>
            <div class="col-md-2"><input v-model="params.sortField" class="form-control"></div>
            <div class="col-md-2"><input v-model="params.sortDir" class="form-control"></div>
            <div class="col-md-2"><button class="btn btn-primary w-100" @click="load">Tải</button></div>
          </div>
        </div></div>
        <div class="card shadow-sm"><div class="card-body p-0">
          <table class="table mb-0"><thead><tr><th>Mã đơn</th><th>Sản phẩm</th><th>Số lượng</th><th>Thành tiền</th></tr></thead>
            <tbody><tr v-for="r in rows" :key="r.orderId + '-' + r.productId"><td>#{{ r.orderId }}</td><td>{{ r.productName }}</td><td>{{ r.quantity }}</td><td>{{ money(r.lineTotal) }}</td></tr></tbody>
          </table>
        </div></div>
        <div class="mt-3 fs-5">Tổng doanh thu: <strong>{{ money(total) }} đ</strong></div>
      </div>
    `
};

const AdminVipPage = {
    setup() {
        const rows = ref([]);
        const load = async () => {
            rows.value = (await api.admin.reports.vip()).data || [];
        };
        onMounted(load);
        return {rows, load, money};
    },
    template: `
      <div>
        <h3>Khách hàng VIP</h3>
        <button class="btn btn-primary mb-3" @click="load">Tải lại</button>
        <div class="card shadow-sm"><div class="card-body p-0">
          <table class="table mb-0"><thead><tr><th>#</th><th>Username</th><th>Họ tên</th><th>Tổng chi tiêu</th></tr></thead>
            <tbody><tr v-for="(r,i) in rows" :key="r.username"><td>{{ i+1 }}</td><td>{{ r.username }}</td><td>{{ r.fullname }}</td><td>{{ money(r.totalAmount) }}</td></tr></tbody>
          </table>
        </div></div>
      </div>
    `
};

const AdminCameraPage = {
    setup() {
        const url = ref("");
        const load = async () => {
            const data = (await api.admin.camera.info()).data || {};
            url.value = data.cameraStreamUrl || "";
        };
        onMounted(load);
        return {url, load};
    },
    template: `
      <div>
        <h3>Camera realtime</h3>
        <button class="btn btn-primary mb-3" @click="load">Làm mới</button>
        <div class="card shadow-sm">
          <div class="card-body">
            <div class="mb-2"><strong>Stream URL:</strong> {{ url }}</div>
            <video v-if="url" :src="url" controls autoplay muted style="width:100%;max-height:520px;background:#000"></video>
          </div>
        </div>
      </div>
    `
};

export const routeMappings = [
    {path: "/auth/login", component: LoginPage, title: "auth/login", template: "auth/login.html"},
    {path: "/home/index", component: HomePage, title: "home/index", template: "home/index.html"},
    {path: "/product/list", component: ProductListPage, title: "product/list", template: "product/list.html"},
    {path: "/product/detail", component: ProductDetailPage, title: "product/detail", template: "product/detail.html"},
    {path: "/cart/index", component: CartPage, title: "cart/index", template: "cart/index.html"},
    {path: "/order/check-out", component: CheckoutPage, title: "order/check-out", template: "order/check-out.html"},
    {path: "/order/bank-transfer", component: BankTransferPage, title: "order/bank-transfer", template: "order/bank-transfer.html"},
    {path: "/order/order-list", component: OrderListPage, title: "order/order-list", template: "order/order-list.html"},
    {path: "/order/order-detail", component: OrderDetailPage, title: "order/order-detail", template: "order/order-detail.html"},
    {path: "/order/my-product-list", component: MyProductListPage, title: "order/my-product-list", template: "order/my-product-list.html"},
    {path: "/account/sign-up", component: SignUpPage, title: "account/sign-up", template: "account/sign-up.html"},
    {path: "/account/edit-profile", component: EditProfilePage, title: "account/edit-profile", template: "account/edit-profile.html"},
    {path: "/account/change-password", component: ChangePasswordPage, title: "account/change-password", template: "account/change-password.html"},
    {path: "/account/forgot-password", component: ForgotPasswordPage, title: "account/forgot-password", template: "account/forgot-password.html"},
    {path: "/admin/account", component: AdminAccountPage, title: "admin/account", template: "admin/account.html"},
    {path: "/admin/category", component: AdminCategoryPage, title: "admin/category", template: "admin/category.html"},
    {path: "/admin/product", component: AdminProductPage, title: "admin/product", template: "admin/product.html"},
    {path: "/admin/order", component: AdminOrderPage, title: "admin/order", template: "admin/order.html"},
    {path: "/admin/revenue", component: AdminRevenuePage, title: "admin/revenue", template: "admin/revenue.html"},
    {path: "/admin/vip", component: AdminVipPage, title: "admin/vip", template: "admin/vip.html"},
    {path: "/admin/camera", component: AdminCameraPage, title: "admin/camera", template: "admin/camera.html"}
];

export const templateCoverage = routeMappings.map((item) => item.template);
export const componentByPath = Object.fromEntries(routeMappings.map((item) => [item.path, item.component]));
export {
    HomePage,
    LoginPage,
    ProductListPage,
    ProductDetailPage,
    CartPage,
    CheckoutPage,
    BankTransferPage,
    OrderListPage,
    OrderDetailPage,
    MyProductListPage,
    SignUpPage,
    EditProfilePage,
    ChangePasswordPage,
    ForgotPasswordPage,
    AdminAccountPage,
    AdminCategoryPage,
    AdminProductPage,
    AdminOrderPage,
    AdminRevenuePage,
    AdminVipPage,
    AdminCameraPage,
    money,
    dateTime,
    productCard
};
