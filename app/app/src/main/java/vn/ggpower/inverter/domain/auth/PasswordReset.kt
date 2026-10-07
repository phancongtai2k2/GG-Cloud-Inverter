package vn.ggpower.inverter.domain.auth

/** Dấu che giữa (ForgotPasswordOtp.dc.html "0912 ••• 678"). */
private const val MASK = "•••"

/**
 * Nơi nhận mã hiển thị ở bước nhập mã (12b, 13b): số điện thoại giữ 4 số đầu và 3 số cuối ("0912 ••• 678"); email
 * giữ ký tự đầu và tên miền ("d•••@ggp.vn", BO_SUNG §17). Chỉ nhận [AccountInput.Phone] / [AccountInput.Email].
 */
fun maskedDestination(account: AccountInput): String = when (account) {
    is AccountInput.Phone -> "${account.value.take(4)} $MASK ${account.value.takeLast(3)}"
    is AccountInput.Email -> {
        val (name, domain) = account.value.split('@', limit = 2)
        "${name.take(1)}$MASK@$domain"
    }
    AccountInput.Empty, AccountInput.Invalid -> throw IllegalArgumentException("Chưa có tài khoản hợp lệ")
}
