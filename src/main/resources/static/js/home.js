/**
 * Module điều khiển tương tác trang chủ EduRepo (Home Page Script).
 * Quản lý các hiệu ứng chuyển động, đếm số liệu thống kê và tương tác thẻ tài liệu nổi bật.
 */

const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
document.addEventListener("DOMContentLoaded", () => {
    const home = document.querySelector(".home-page");
    const illustration = home?.querySelector(".learning-illustration");
    if (!illustration || reduceMotion) return;
    illustration.addEventListener("pointermove", (event) => {
        const box = illustration.getBoundingClientRect();
        const x = (event.clientX - box.left) / box.width - .5;
        const y = (event.clientY - box.top) / box.height - .5;
        illustration.style.transform = `perspective(900px) rotateY(${x * 3}deg) rotateX(${y * -3}deg)`;
    });
    illustration.addEventListener("pointerleave", () => { illustration.style.transform = ""; });
});
