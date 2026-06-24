# ─────────────────────────────────────────────────────────────────
# iptv-speedtest Android .so 构建
#
# 使用方式（在 my-tv 仓库根目录执行）：
#   make -f Makefile-speedtest.mk          # 编译全部 4 个架构
#   make -f Makefile-speedtest.mk arm64    # 仅编译 arm64-v8a
#   make -f Makefile-speedtest.mk clean    # 清理产物
#
# 前置要求：
#   1. rustup target add aarch64-linux-android armv7-linux-androideabi \
#                        x86_64-linux-android i686-linux-android
#   2. 安装 Android NDK，并设置环境变量：
#        export ANDROID_NDK_HOME=/path/to/ndk   (或 NDK_HOME)
#      NDK 版本建议 r25c 或更新；minSdkVersion 21 以上均可。
#   3. cargo 已安装（rustup 附带）
# ─────────────────────────────────────────────────────────────────

SPEEDTEST_DIR := iptv-speedtest
JNILIBS_DIR  := app/src/main/jniLibs
BIN_NAME     := iptv_speedtest
SO_NAME      := libiptv_speedtest.so

# NDK 路径：优先取环境变量，也可以在这里硬编码
NDK_HOME ?= $(ANDROID_NDK_HOME)
ifeq ($(NDK_HOME),)
$(error 请设置 ANDROID_NDK_HOME 或 NDK_HOME 环境变量指向 Android NDK 根目录)
endif

# NDK toolchain
TOOLCHAIN := $(NDK_HOME)/toolchains/llvm/prebuilt/linux-x86_64/bin
# 如果是 macOS：
# TOOLCHAIN := $(NDK_HOME)/toolchains/llvm/prebuilt/darwin-x86_64/bin

# API level（对应 app/build.gradle 里的 minSdkVersion）
API := 21

# ── Cargo 公共参数 ─────────────────────────────────────────────
CARGO_FLAGS := --manifest-path $(SPEEDTEST_DIR)/Cargo.toml \
               --profile release-android \
               --no-default-features \
               --features android

# ── 架构映射 ──────────────────────────────────────────────────
#   Rust target triple → ABI 目录名 + linker
define build_arch
	@echo "▶ 编译 $(1) → $(2)"
	CC_$(subst -,_,$(1))=$(TOOLCHAIN)/$(3)$(API)-clang \
	CARGO_TARGET_$(call upper_dash,$(1))_LINKER=$(TOOLCHAIN)/$(3)$(API)-clang \
	cargo build $(CARGO_FLAGS) --target $(1)
	@mkdir -p $(JNILIBS_DIR)/$(2)
	cp $(SPEEDTEST_DIR)/target/$(1)/release-android/$(BIN_NAME) \
	   $(JNILIBS_DIR)/$(2)/$(SO_NAME)
	@echo "  ✓ $(JNILIBS_DIR)/$(2)/$(SO_NAME)"
endef

# 把 target triple 的 - 换成 _ 并大写（Cargo 环境变量命名规则）
upper_dash = $(shell echo $(1) | tr 'a-z-' 'A-Z_')

# ── Targets ───────────────────────────────────────────────────
.PHONY: all arm64 arm x86_64 x86 clean

all: arm64 arm x86_64 x86

arm64:
	$(call build_arch,aarch64-linux-android,arm64-v8a,aarch64-linux-android)

arm:
	$(call build_arch,armv7-linux-androideabi,armeabi-v7a,armv7a-linux-androideabi)

x86_64:
	$(call build_arch,x86_64-linux-android,x86_64,x86_64-linux-android)

x86:
	$(call build_arch,i686-linux-android,x86,i686-linux-android)

clean:
	cargo clean --manifest-path $(SPEEDTEST_DIR)/Cargo.toml
	rm -f $(JNILIBS_DIR)/arm64-v8a/$(SO_NAME) \
	      $(JNILIBS_DIR)/armeabi-v7a/$(SO_NAME) \
	      $(JNILIBS_DIR)/x86_64/$(SO_NAME) \
	      $(JNILIBS_DIR)/x86/$(SO_NAME)
