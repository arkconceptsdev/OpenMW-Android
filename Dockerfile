# syntax=docker/dockerfile:labs
FROM fedora:43@sha256:a651ddf48ea28a06ed4e1e6519f51c9f47e7a5a138722ade87369b8fbb7e5b42 AS build

#Set build type : release, RelWithDebInfo, debug
ENV BUILD_TYPE=release
ARG NDK_VERSION=r29
ENV API=26
ENV ABI=arm64-v8a
ENV ARCH=aarch64

# App versions - change settings here
ARG LIBJPEG_TURBO_VERSION=3.1.0
ARG LIBPNG_VERSION=1.6.48
ARG FREETYPE2_VERSION=2.13.3
ARG OBOE_VERSION=1.9.3
ARG OPENAL_VERSION=1.24.3
ARG BOOST_VERSION=1.88.0
ARG LIBICU_VERSION=70-1
ARG FFMPEG_VERSION=7.1.1
ARG SDL2_VERSION=2.32.4
ARG BULLET_VERSION=3.25
ARG ZLIB_VERSION=1.3.1
ARG LIBXML2_VERSION=2.14.3
ARG MYGUI_VERSION=3.4.3
ARG GL4ES_VERSION=72d0029baf1de0b6a85244680316132a4c244164
ARG GL4ES_COMMIT=72d0029baf1de0b6a85244680316132a4c244164
ARG COLLADA_DOM_VERSION=2.5.0
ARG OSG_VERSION=8aa8d91747ff414ab0de19aae1e0dbd739acea2c
ARG LZ4_VERSION=1.10.0
ARG LUAJIT_VERSION=2.1
ARG OPENMW_VERSION=dbbd9456e8d3d643ec41bb1331ef4b607db20a04
ARG BZIP2_COMMIT=1ea1ac188ad4b9cb662e3f8314673c63df95a589
ARG UQM_COMMIT=b6b118a04cfa3572a7c2cf3a8dc02eed96705340
ARG DETHRACE_COMMIT=9448ebefeb66e4b31b130b53d3534868cff2d89b
ARG S3LIGHTFIXES_COMMIT=fcba01173a0eb36c58e0ee30c8ebea8295d806e8
ARG SDK_CMDLINE_TOOLS=10406996_latest
ARG JAVA_VERSION=21
ARG RUST_NIGHTLY=nightly-2026-09-25

# NDK Settings
ENV NDK_TRIPLET=${ARCH}-linux-android
ENV TOOLCHAIN=/root/Android/android-ndk-${NDK_VERSION}/toolchains/llvm/prebuilt/linux-x86_64
ENV NDK_SYSROOT=${TOOLCHAIN}/sysroot/
ENV ANDROID_SYSROOT=${TOOLCHAIN}/sysroot/
ENV ANDROID_NDK=/root/Android/ndk/${NDK_VERSION}/
ENV ANDROID_HOME=/root/Android
ENV PREFIX=/root/prefix

# Set global path
ENV PATH=$PATH:/root/Android/cmdline-tools/latest/bin/:/root/Android/android-ndk-${NDK_VERSION}/:/root/Android/android-ndk-${NDK_VERSION}/toolchains/llvm/prebuilt/linux-x86_64:/root/Android/android-ndk-${NDK_VERSION}/toolchains/llvm/prebuilt/linux-x86_64/bin:/root/prefix/include:/root/prefix/lib:/root/prefix/:/root/.cargo/bin

RUN mkdir -p ${HOME}/{prefix,src}

RUN dnf install -y xz p7zip bzip2 libstdc++-devel glibc-devel zip unzip libcurl-devel which libvorbis-devel.x86_64 openal-soft-devel.x86_64 \
    libogg-devel.x86_64 libpng-devel.x86_64 zlib-devel wget python-devel doxygen nano gcc-c++ libxcb-devel git java-${JAVA_VERSION}-openjdk-devel cmake patch SDL2 SDL2-devel \
    mingw32-gcc mingw32-gcc-c++ mingw32-binutils mingw32-crt mingw32-winpthreads mingw32-openal mingw32-SDL2 mingw32-zlib mingw32-libpng jq

# Setup the NDK
RUN curl -O https://dl.google.com/android/repository/android-ndk-${NDK_VERSION}-linux.zip && unzip android-ndk-${NDK_VERSION}-linux.zip -d /root/Android && rm ./android-ndk-${NDK_VERSION}-linux.zip

# Setup sdkmanager and all tools
RUN wget https://dl.google.com/android/repository/commandlinetools-linux-${SDK_CMDLINE_TOOLS}.zip && unzip commandlinetools-linux-${SDK_CMDLINE_TOOLS}.zip && mkdir -p ${HOME}/Android/cmdline-tools/ && mv cmdline-tools/ ${HOME}/Android/cmdline-tools/latest && rm commandlinetools-linux-${SDK_CMDLINE_TOOLS}.zip
RUN yes | ~/Android/cmdline-tools/latest/bin/sdkmanager --licenses > /dev/null

#Setup ICU for the Host
RUN cd ${HOME}/src && wget https://github.com/unicode-org/icu/archive/refs/tags/release-${LIBICU_VERSION}.zip && unzip -o ${HOME}/src/release-${LIBICU_VERSION}.zip && rm -rf release-${LIBICU_VERSION}.zip
RUN mkdir -p ${HOME}/src/icu-host-build && cd $_ && ${HOME}/src/icu-release-70-1/icu4c/source/configure --disable-tests --disable-samples --disable-icuio --disable-extras CC="gcc" CXX="g++" && make -j $(nproc)

ENV PKG_CONFIG_LIBDIR=${PREFIX}/lib/pkgconfig

# Global C, CXX and LDFLAGS
ENV CFLAGS="-fPIC -O3 -flto=thin"
ENV CXXFLAGS="-fPIC -O3 -frtti -fexceptions -flto=thin"
ENV LDFLAGS="-fPIC -Wl,--undefined-version -flto=thin -fuse-ld=lld"

ENV COMMON_AUTOCONF_FLAGS="--enable-static \
  --disable-shared \
  --prefix=${PREFIX} \
  --host=${NDK_TRIPLET}${API} \
  CC=${TOOLCHAIN}/bin/${NDK_TRIPLET}${API}-clang \
  CXX=${TOOLCHAIN}/bin/${NDK_TRIPLET}${API}-clang++"

ENV NDK_BUILD_FLAGS="NDK_PROJECT_PATH=. APP_BUILD_SCRIPT=./Android.mk APP_PLATFORM=${API} APP_ABI=${ABI}"

ENV COMMON_CMAKE_ARGS="-DCMAKE_TOOLCHAIN_FILE=/root/Android/android-ndk-${NDK_VERSION}/build/cmake/android.toolchain.cmake \
  -DANDROID_ABI=${ABI} \
  -DANDROID_PLATFORM=${API} \
  -DANDROID_STL=c++_shared \
  -DANDROID_CPP_FEATURES= \
  -DANDROID_ALLOW_UNDEFINED_VERSION_SCRIPT_SYMBOLS=ON \
  -DCMAKE_BUILD_TYPE=$BUILD_TYPE \
  -DCMAKE_C_FLAGS=-I${PREFIX} \
  -DCMAKE_DEBUG_POSTFIX= \
  -DCMAKE_INSTALL_PREFIX=${PREFIX} \
  -DCMAKE_FIND_ROOT_PATH=${PREFIX} \
  -DHAVE_LD_VERSION_SCRIPT=OFF"

# Setup rust build system for android
ARG NDK_REVISION=29.0.14206865
ARG RUST_STABLE_VERSION=1.98.1
RUN ACTUAL_NDK_REVISION=$(grep 'Pkg.Revision' /root/Android/android-ndk-${NDK_VERSION}/source.properties | cut -d'=' -f2 | tr -d '[:space:]') && \
    if [ "$ACTUAL_NDK_REVISION" != "$NDK_REVISION" ]; then \
      echo "Expected Android NDK ${NDK_REVISION}, got ${ACTUAL_NDK_REVISION}" >&2; \
      exit 1; \
    fi
RUN wget https://sh.rustup.rs -O rustup.sh && sha256sum rustup.sh && \
    echo "7d0ea0f8eba7fa1ebfe998091cd7ec4501e33ec5ca6b884eb4d894d7da5170af  rustup.sh" | sha256sum -c - && \
    sh rustup.sh -y --default-toolchain ${RUST_STABLE_VERSION} && rm rustup.sh && \
    ${HOME}/.cargo/bin/rustup target add ${NDK_TRIPLET} && \
    ${HOME}/.cargo/bin/rustup toolchain install ${RUST_NIGHTLY} && \
    ${HOME}/.cargo/bin/rustup target add --toolchain ${RUST_NIGHTLY} ${NDK_TRIPLET} && \
    echo "[target.${NDK_TRIPLET}]" >> /root/.cargo/config && \
    echo "linker = \"${TOOLCHAIN}/bin/${NDK_TRIPLET}${API}-clang\"" >> /root/.cargo/config

# Setup LIBICU
RUN mkdir -p ${HOME}/src/icu-${LIBICU_VERSION} && cd $_ && \
    ${HOME}/src/icu-release-${LIBICU_VERSION}/icu4c/source/configure \
        ${COMMON_AUTOCONF_FLAGS} \
        --disable-tests \
        --disable-samples \
        --disable-icuio \
        --disable-extras \
        --prefix=${PREFIX} \
        --with-cross-build=/root/src/icu-host-build && \
    make -j $(nproc) check_PROGRAMS= bin_PROGRAMS= && \
    make install check_PROGRAMS= bin_PROGRAMS=

# Setup Bzip2
RUN cd $HOME/src/ && git clone https://github.com/libarchive/bzip2 && cd bzip2 && \
    git checkout ${BZIP2_COMMIT} && \
    cmake . \
        $COMMON_CMAKE_ARGS && \
    make -j $(nproc) && make install

# Setup ZLIB
RUN wget -c https://github.com/madler/zlib/archive/refs/tags/v${ZLIB_VERSION}.tar.gz -O - | tar -xz -C $HOME/src/ && \
    mkdir -p ${HOME}/src/zlib-${ZLIB_VERSION}/build && cd $_ && \
    cmake ../ \
        ${COMMON_CMAKE_ARGS} && \
    make -j $(nproc) && make install

# Setup LIBJPEG_TURBO
RUN wget -c https://github.com/libjpeg-turbo/libjpeg-turbo/releases/download/${LIBJPEG_TURBO_VERSION}/libjpeg-turbo-${LIBJPEG_TURBO_VERSION}.tar.gz -O - | tar -xz -C $HOME/src/ && \
    mkdir -p ${HOME}/src/libjpeg-turbo-${LIBJPEG_TURBO_VERSION}/build && cd $_ && \
    cmake ../ \
        ${COMMON_CMAKE_ARGS} \
        -DENABLE_SHARED=false && \
    make -j $(nproc) && make install

# Setup LIBPNG
RUN wget -c http://prdownloads.sourceforge.net/libpng/libpng-${LIBPNG_VERSION}.tar.gz -O - | tar -xz -C $HOME/src/ && \
    mkdir -p ${HOME}/src/libpng-${LIBPNG_VERSION}/build && cd $_ && \
        ${HOME}/src/libpng-${LIBPNG_VERSION}/configure \
        ${COMMON_AUTOCONF_FLAGS} && \
    make -j $(nproc) check_PROGRAMS= bin_PROGRAMS= && \
    make install check_PROGRAMS= bin_PROGRAMS=

# Setup FREETYPE2
RUN wget -c http://prdownloads.sourceforge.net/freetype/freetype-${FREETYPE2_VERSION}.tar.gz -O - | tar -xz -C $HOME/src/ && \
    mkdir -p ${HOME}/src/freetype-${FREETYPE2_VERSION}/build && cd $_ && \
    cmake ../ \
        ${COMMON_CMAKE_ARGS} \
        -DCMAKE_DISABLE_FIND_PACKAGE_ZLIB=TRUE \
        -DCMAKE_DISABLE_FIND_PACKAGE_BZip2=TRUE \
        -DCMAKE_DISABLE_FIND_PACKAGE_PNG=TRUE && \
    make -j $(nproc) && make install

# Setup LIBXML
RUN wget -c https://github.com/GNOME/libxml2/archive/refs/tags/v${LIBXML2_VERSION}.tar.gz -O - | tar -xz -C $HOME/src/ && \
    mkdir -p ${HOME}/src/libxml2-${LIBXML2_VERSION}/build && cd $_ && \
    cmake ../ \
        ${COMMON_CMAKE_ARGS} \
        -DBUILD_SHARED_LIBS=OFF \
        -DLIBXML2_WITH_THREADS=ON \
        -DLIBXML2_WITH_CATALOG=OFF \
        -DLIBXML2_WITH_ICONV=OFF \
        -DLIBXML2_WITH_LZMA=OFF \
        -DLIBXML2_WITH_PROGRAMS=OFF \
        -DLIBXML2_WITH_PYTHON=OFF \
        -DLIBXML2_WITH_TESTS=OFF \
        -DLIBXML2_WITH_ZLIB=ON && \
    make -j $(nproc) && make install

# Setup OBOE
RUN wget -c https://github.com/google/oboe/archive/refs/tags/${OBOE_VERSION}.tar.gz -O - | tar -xz -C $HOME/src/

# Setup OPENAL
RUN wget -c https://github.com/kcat/openal-soft/archive/${OPENAL_VERSION}.tar.gz -O - | tar -xz -C $HOME/src/ && \
    mkdir -p ${HOME}/src/openal-soft-${OPENAL_VERSION}/build && cd $_ && \
    cmake ../ \
        ${COMMON_CMAKE_ARGS} \
        -DALSOFT_EXAMPLES=OFF \
        -DALSOFT_TESTS=OFF \
        -DALSOFT_UTILS=OFF \
        -DALSOFT_NO_CONFIG_UTIL=ON \
        -DALSOFT_BACKEND_OPENSL=OFF \
        -DALSOFT_BACKEND_OBOE=ON \
        -DOBOE_SOURCE=${HOME}/src/oboe-${OBOE_VERSION} \
        -DALSOFT_BACKEND_WAVE=OFF && \
    make -j $(nproc) && make install

# Setup BOOST
RUN wget -c https://github.com/boostorg/boost/releases/download/boost-${BOOST_VERSION}/boost-${BOOST_VERSION}-cmake.tar.gz -O - | tar -xz -C $HOME/src/ && \
    mkdir -p ${HOME}/src/boost-${BOOST_VERSION}/build && cd $_ && \
    cmake ../ ${COMMON_CMAKE_ARGS} \
        -DBOOST_INCLUDE_LIBRARIES="filesystem;program_options;iostreams;geometry" && \
    make -j $(nproc) && make install
RUN ${TOOLCHAIN}/bin/llvm-ar rc ${PREFIX}/lib/libboost_system.a $(find / -name "error_code.o" 2>/dev/null)
RUN ${TOOLCHAIN}/bin/llvm-ar rc ${PREFIX}/lib/libboost_regex.a $(find / \( -name "posix_api.o" -o -name "regex.o" -o -name "regex_debug.o" -o -name "static_mutex.o" -o -name "wide_posix_api.o" \) 2>/dev/null)
RUN ${TOOLCHAIN}/bin/llvm-ranlib ${PREFIX}/lib/libboost_{system,filesystem,program_options,iostreams,regex}.a

# Setup FFMPEG_VERSION
RUN wget -c https://github.com/FFmpeg/FFmpeg/archive/refs/tags/n${FFMPEG_VERSION}.tar.gz -O - | \
    tar -xzf - -C ${HOME}/src/ && \
    mv ${HOME}/src/FFmpeg-n${FFMPEG_VERSION} ${HOME}/src/ffmpeg-${FFMPEG_VERSION}
RUN mkdir -p ${HOME}/src/ffmpeg-${FFMPEG_VERSION} && cd $_ && \
    ${HOME}/src/ffmpeg-${FFMPEG_VERSION}/configure \
        --disable-asm \
        --disable-optimizations \
        --target-os=android \
        --enable-cross-compile \
        --cross-prefix=${TOOLCHAIN}/bin/llvm- \
        --cc=${TOOLCHAIN}/bin/${NDK_TRIPLET}${API}-clang \
        --arch=arm64 \
        --cpu=armv8-a \
        --prefix=${PREFIX} \
        --enable-version3 \
        --enable-pic \
        --disable-everything \
        --disable-doc \
        --disable-programs \
        --disable-autodetect \
        --disable-iconv \
        --enable-decoder=mp3 \
        --enable-demuxer=mp3 \
        --enable-decoder=bink \
        --enable-decoder=binkaudio_rdft \
        --enable-decoder=binkaudio_dct \
        --enable-demuxer=bink \
        --enable-demuxer=wav \
        --enable-decoder=pcm_* \
        --enable-decoder=vp8 \
        --enable-decoder=vp9 \
        --enable-decoder=opus \
        --enable-decoder=vorbis \
        --enable-demuxer=matroska \
        --enable-demuxer=ogg && \
    make -j $(nproc) && make install

# Setup SDL2_VERSION
RUN wget -c https://github.com/libsdl-org/SDL/releases/download/release-${SDL2_VERSION}/SDL2-${SDL2_VERSION}.tar.gz -O - | tar -xz -C ${HOME}/src/ && \
    mkdir -p ${HOME}/src/SDL2-${SDL2_VERSION}/build && cd $_ && \
    cmake ../ ${COMMON_CMAKE_ARGS} \
        -DSDL_STATIC=OFF \
        -DHAVE_GCC_FVISIBILITY=OFF && \
    make -j $(nproc) && make install
RUN cp -rf ${HOME}/src/SDL2-${SDL2_VERSION}/include/* /root/prefix/include/

# Setup BULLET
RUN wget -c https://github.com/bulletphysics/bullet3/archive/${BULLET_VERSION}.tar.gz -O - | tar -xz -C $HOME/src/ && \
    mkdir -p ${HOME}/src/bullet3-${BULLET_VERSION}/build && cd $_ && \
    cmake ../ \
        ${COMMON_CMAKE_ARGS} \
        -DBUILD_BULLET2_DEMOS=OFF \
        -DBUILD_CPU_DEMOS=OFF \
        -DBUILD_UNIT_TESTS=OFF \
        -DBUILD_EXTRAS=OFF \
        -DUSE_DOUBLE_PRECISION=ON \
        -DBULLET2_MULTITHREADING=ON && \
    make -j $(nproc) && make install

COPY --chmod=0755 patches/gl4es /root/patches/gl4es

# patch -d ${HOME}/src/NG-GL4ES-${GL4ES_VERSION} -p1 -t -N < /root/patches/gl4es/debug.patch && \
# Setup GL4ES_VERSION
RUN git clone --recurse-submodules https://github.com/sisah2/Ng-gl4es -b Openmw3 $HOME/src/NG-GL4ES-${GL4ES_VERSION} && \
    git -C $HOME/src/NG-GL4ES-${GL4ES_VERSION} checkout ${GL4ES_COMMIT} && \
    git -C $HOME/src/NG-GL4ES-${GL4ES_VERSION} submodule update --init --recursive
RUN mkdir -p ${HOME}/src/NG-GL4ES-${GL4ES_VERSION}/build && cd $_ && \
    patch -d ${HOME}/src/NG-GL4ES-${GL4ES_VERSION} -p1 -t -N < /root/patches/gl4es/logPath.patch && \
    cmake ../ ${COMMON_CMAKE_ARGS} && \
    make -j $(nproc) && \
    cp -r ${HOME}/src/NG-GL4ES-${GL4ES_VERSION}/build/*.so ${PREFIX}/lib/ && \
    cp -r /root/src/NG-GL4ES-${GL4ES_VERSION}/include /root/prefix/ && \
    cp -r /root/src/NG-GL4ES-${GL4ES_VERSION}/include /root/prefix/include/gl4es

# Setup MYGUI
RUN wget -c https://github.com/MyGUI/mygui/archive/MyGUI${MYGUI_VERSION}.tar.gz -O - | tar -xz -C $HOME/src/ && \
    cd ${HOME}/src/mygui-MyGUI${MYGUI_VERSION} && \
    sed -i 's/using unicode_char = uint32;/using unicode_char = char32_t;/g' MyGUIEngine/include/MyGUI_UString.h && \
    sed -i 's/using code_point = uint16;/using code_point = char16_t;/g' MyGUIEngine/include/MyGUI_UString.h && \
    mkdir -p ${HOME}/src/mygui-MyGUI${MYGUI_VERSION}/build && cd $_ && \
    cmake ../ \
        ${COMMON_CMAKE_ARGS} \
        -DMYGUI_RENDERSYSTEM=1 \
        -DMYGUI_BUILD_DEMOS=OFF \
        -DMYGUI_BUILD_TOOLS=OFF \
        -DMYGUI_BUILD_PLUGINS=OFF \
        -DMYGUI_DONT_USE_OBSOLETE=ON \
        -DMYGUI_STATIC=ON && \
    make -j $(nproc) && make install

# Setup LZ4
RUN wget -c https://github.com/lz4/lz4/archive/v${LZ4_VERSION}.tar.gz -O - | tar -xz -C $HOME/src/ && \
    mkdir -p ${HOME}/src/lz4-${LZ4_VERSION}/build && cd $_ && \
    cmake cmake/ \
        ${COMMON_CMAKE_ARGS} \
        -DBUILD_STATIC_LIBS=ON \
        -DBUILD_SHARED_LIBS=OFF && \
    make -j $(nproc) && make install

# Setup LUAJIT_VERSION
RUN wget -c https://github.com/luaJit/LuaJIT/archive/v${LUAJIT_VERSION}.tar.gz -O - | tar -xz -C ${HOME}/src/ && \
    cd ${HOME}/src/LuaJIT-${LUAJIT_VERSION} && \
    make amalg \
    HOST_CC='gcc -m64' \
    CFLAGS= \
    TARGET_CFLAGS="${CFLAGS}" \
    PREFIX=${PREFIX} \
    CROSS=${TOOLCHAIN}/bin/llvm- \
    STATIC_CC=${TOOLCHAIN}/bin/${NDK_TRIPLET}${API}-clang \
    DYNAMIC_CC="${TOOLCHAIN}/bin/${NDK_TRIPLET}${API}-clang -fPIC" \
    TARGET_LD=${TOOLCHAIN}/bin/${NDK_TRIPLET}${API}-clang && \
    make install \
    HOST_CC='gcc -m64' \
    CFLAGS= \
    TARGET_CFLAGS="${CFLAGS}" \
    PREFIX=${PREFIX} \
    CROSS=${TOOLCHAIN}/bin/llvm- \
    STATIC_CC=${TOOLCHAIN}/bin/${NDK_TRIPLET}${API}-clang \
    DYNAMIC_CC="${TOOLCHAIN}/bin/${NDK_TRIPLET}${API}-clang -fPIC" \
    TARGET_LD=${TOOLCHAIN}/bin/${NDK_TRIPLET}${API}-clang

RUN rm ${PREFIX}/lib/libluajit*.so*

# Setup Libogg
RUN wget -c https://github.com/xiph/ogg/releases/download/v1.3.5/libogg-1.3.5.tar.gz -O - | tar -xz -C ${HOME}/src/ && cd ${HOME}/src/libogg-1.3.5 && \
    mkdir -p ${HOME}/src/libogg-1.3.5/build && cd $_ && \
    cmake .. \
        ${COMMON_CMAKE_ARGS} && \
    make -j $(nproc) && make install

# Setup Vorbis
RUN wget -c https://github.com/xiph/vorbis/releases/download/v1.3.7/libvorbis-1.3.7.tar.gz -O - | tar -xz -C ${HOME}/src/ && cd ${HOME}/src/libvorbis-1.3.7 && \
    mkdir -p ${HOME}/src/libvorbis-1.3.7/build && cd $_ && \
    cmake .. \
        ${COMMON_CMAKE_ARGS} && \
    make -j $(nproc) && make install

RUN cd /root/src && git clone --branch master --single-branch https://github.com/JHGuitarFreak/UQM-MegaMod.git && \
    git -C /root/src/UQM-MegaMod checkout ${UQM_COMMIT}

RUN mkdir /root/src/UQM-MegaMod/Android && cd $_ && \
    cmake .. \
        ${COMMON_CMAKE_ARGS} \
        -DCMAKE_C_FLAGS="-I/root/prefix -Wno-error=format-security" \
        -DCMAKE_SHARED_LINKER_FLAGS="-L/root/prefix/lib" && \
    make -j $(nproc)
RUN cp /root/src/UQM-MegaMod/Android/libUrQuanMasters.so /libuqm.so

RUN cd /root/src && git clone --recurse-submodules https://github.com/Duron27-org/dethrace.git && \
    git -C /root/src/dethrace checkout ${DETHRACE_COMMIT} && \
    git -C /root/src/dethrace submodule update --init --recursive
RUN mkdir -p ${HOME}/src/dethrace/build && cd $_ && \
    cmake .. \
        ${COMMON_CMAKE_ARGS} \
        -DDETHRACE_ANDROID=ON \
        -DDETHRACE_PLATFORM_SDL2=ON \
        -DSDL2_INCLUDE_DIR=/root/prefix/include \
        -DSDL2_LIBRARY=/root/prefix/lib/libSDL2.so && \
    make -j$(nproc)
RUN cp /root/src/dethrace/build/src/DETHRACE/libdethrace.so /

# Setup LIBCOLLADA_VERSION
# The 3 sed commands are required for boost 1.85.0
RUN wget -c https://github.com/rdiankov/collada-dom/archive/v${COLLADA_DOM_VERSION}.tar.gz -O - | tar -xz -C ${HOME}/src/ && cd ${HOME}/src/collada-dom-${COLLADA_DOM_VERSION} && \
    cd ${HOME}/src/collada-dom-${COLLADA_DOM_VERSION} && \
    sed -i 's|#include <boost/filesystem/convenience.hpp>|#include <boost/filesystem.hpp>|g' dom/include/dae.h && \
    sed -i 's|#include <boost/filesystem/convenience.hpp>|#include <boost/filesystem.hpp>|g' dom/src/dae/daeUtils.cpp && \
    sed -i 's|std::string dir = archivePath.branch_path().string();|std::string dir = archivePath.parent_path().string();|g' dom/src/dae/daeZAEUncompressHandler.cpp && \
    mkdir -p ${HOME}/src/collada-dom-${COLLADA_DOM_VERSION}/build && cd $_ && \
    cmake .. \
        ${COMMON_CMAKE_ARGS} \
        -DBoost_USE_STATIC_LIBS=ON \
        -DBoost_USE_STATIC_RUNTIME=ON \
        -DBoost_NO_SYSTEM_PATHS=ON \
        -DBoost_INCLUDE_DIR=${PREFIX}/include \
        -DCMAKE_CXX_FLAGS=-Dauto_ptr=unique_ptr\ "${CXXFLAGS}" && \
    make -j $(nproc) && make install

# Setup Delta Plugin
RUN cd root/src && \
    git clone https://gitlab.com/bmwinger/delta-plugin && \
    cd delta-plugin && \
    git checkout 9c40703c7481635ebfc0933941e88c36a8c8b282 && \
    cargo build --target ${NDK_TRIPLET} --release
RUN cp /root/src/delta-plugin/target/${NDK_TRIPLET}/release/delta_plugin ${PREFIX}/lib/libdelta_plugin.so

# Setup S3LightFixes
RUN cd root/src && git clone https://github.com/magicaldave/S3LightFixes && \
    cd S3LightFixes && git checkout ${S3LIGHTFIXES_COMMIT} && \
    cargo build --target ${NDK_TRIPLET} --release
RUN cp /root/src/S3LightFixes/target/${NDK_TRIPLET}/release/s3lightfixes ${PREFIX}/lib/libS3LightFixes.so

COPY --chmod=0755 patches/osg /root/patches/osg

RUN wget -c https://github.com/openmw/osg/archive/${OSG_VERSION}.tar.gz -O - | tar -xz -C ${HOME}/src/ && \
    mkdir -p ${HOME}/src/osg-${OSG_VERSION}/build && cd $_ && \
    patch -d ${HOME}/src/osg-${OSG_VERSION} -p1 -t -N < /root/patches/osg/disable-polygon-offset.patch && \
    patch -d ${HOME}/src/osg-${OSG_VERSION} -p1 -t -N < /root/patches/osg/remove-lib-prefix-from-plugins.patch && \
    patch -d ${HOME}/src/osg-${OSG_VERSION} -p1 -t -N < /root/patches/osg/fix-freetype-include-dirs.patch && \
    patch -d ${HOME}/src/osg-${OSG_VERSION} -p1 -t -N < /root/patches/osg/0001-Replace-Atomic-impl-with-std-atomic.patch && \
    patch -d ${HOME}/src/osg-${OSG_VERSION} -p1 -t -N < /root/patches/osg/0002-BufferObject-make-numClients-atomic.patch && \
    patch -d ${HOME}/src/osg-${OSG_VERSION} -p1 -t -N < /root/patches/osg/0004-IncrementalCompileOperation-wrap-some-stuff-in-atomi.patch && \
    patch -d ${HOME}/src/osg-${OSG_VERSION} -p1 -t -N < /root/patches/osg/force-add-plugins.patch && \
    patch -d ${HOME}/src/osg-${OSG_VERSION} -p1 -t -N < /root/patches/osg/dae_collada.patch && \
    patch -d ${HOME}/src/osg-${OSG_VERSION} -p1 -t -N < /root/patches/osg/ng-gl4es.patch && \
    patch -d ${HOME}/src/osg-${OSG_VERSION} -p1 -t -N < /root/patches/osg/wtf.patch && \
    patch -d ${HOME}/src/osg-${OSG_VERSION} -p1 -t -N < /root/patches/osg/0005-CullSettings-make-inheritanceMask-atomic-to-silence-.patch && \
    cmake .. \
        ${COMMON_CMAKE_ARGS} \
        -DOPENGL_PROFILE=GL2 \
        -DDYNAMIC_OPENTHREADS=OFF \
        -DDYNAMIC_OPENSCENEGRAPH=OFF \
        -DBUILD_OSG_PLUGIN_OSG=ON \
        -DBUILD_OSG_PLUGIN_DAE=ON \
        -DBUILD_OSG_PLUGIN_DDS=ON \
        -DBUILD_OSG_PLUGIN_TGA=ON \
        -DBUILD_OSG_PLUGIN_BMP=ON \
        -DBUILD_OSG_PLUGIN_JPEG=ON \
        -DBUILD_OSG_PLUGIN_PNG=ON \
        -DBUILD_OSG_PLUGIN_KTX=ON \
        -DBUILD_OSG_PLUGIN_FREETYPE=ON \
        -DOSG_CPP_EXCEPTIONS_AVAILABLE=TRUE \
        -DJPEG_INCLUDE_DIR=${PREFIX}/include/ \
        -DPNG_INCLUDE_DIR=${PREFIX}/include/ \
        -DCOLLADA_INCLUDE_DIR=${PREFIX}/include/collada-dom2.5 \
        -DCOLLADA_DIR=${PREFIX}/include/collada-dom2.5/1.4 \
        -DOSG_GL1_AVAILABLE=ON \
        -DOSG_GL2_AVAILABLE=ON \
        -DOSG_GL3_AVAILABLE=OFF \
        -DOSG_GLES1_AVAILABLE=OFF \
        -DOSG_GLES3_AVAILABLE=OFF \
        -DOSG_GL_LIBRARY_STATIC=OFF \
        -DOSG_GL_DISPLAYLISTS_AVAILABLE=ON \
        -DOSG_GL_MATRICES_AVAILABLE=ON \
        -DOSG_GL_VERTEX_FUNCS_AVAILABLE=ON \
        -DOSG_GL_VERTEX_ARRAY_FUNCS_AVAILABLE=ON \
        -DOSG_GL_FIXED_FUNCTION_AVAILABLE=ON \
        -DBUILD_OSG_APPLICATIONS=OFF \
        -DBUILD_OSG_PLUGINS_BY_DEFAULT=OFF \
        -DBUILD_OSG_DEPRECATED_SERIALIZERS=OFF \
        -DOSG_FIND_3RD_PARTY_DEPS=OFF \
        -DOPENGL_INCLUDE_DIR=${PREFIX}/include/gl4es/ \
        -DOPENGL_gl_LIBRARY=${PREFIX}/lib/libng_gl4es.so \
        -DCMAKE_CXX_FLAGS=-Dauto_ptr=unique_ptr\ -I${PREFIX}/include/freetype2/\ "${CXXFLAGS}" && \
    make -j $(nproc) && make install

# Create a zip of all the libraries
#RUN cd /root/prefix && zip -r /openmw-android-deps.zip ./*

# Setup OPENMW_VERSION (pick one, COPY or RUN wget)
# For testing changes to openmw itself just clone openmw into the same folder dockerfile is in.
#COPY --chmod=0755 openmw /root/src/openmw-${OPENMW_VERSION}

# Or you can enable this
RUN wget -c https://github.com/OpenMW/openmw/archive/${OPENMW_VERSION}.tar.gz -O - | tar -xz -C ${HOME}/src/

COPY --chmod=0755 patches/openmw /root/patches/openmw
RUN patch -d ${HOME}/src/openmw-${OPENMW_VERSION} -p1 -t -N < /root/patches/openmw/0009-windowmanagerimp-always-show-mouse-when-possible-pat.patch
RUN patch -d ${HOME}/src/openmw-${OPENMW_VERSION} -p1 -t -N < /root/patches/openmw/androidPath.patch
RUN patch -d ${HOME}/src/openmw-${OPENMW_VERSION} -p1 -t -N < /root/patches/openmw/4307.diff
RUN patch -d ${HOME}/src/openmw-${OPENMW_VERSION} -p1 -t -N < /root/patches/openmw/FIXME-composie-maps-mipmaps.patch
RUN patch -d ${HOME}/src/openmw-${OPENMW_VERSION} -p1 -t -N < /root/patches/openmw/FIXME-setGlobalDefines-crash.patch
RUN patch -d ${HOME}/src/openmw-${OPENMW_VERSION} -p1 -t -N < /root/patches/openmw/force-postprocess-glsl-version.patch
RUN patch -d ${HOME}/src/openmw-${OPENMW_VERSION} -p1 -t -N < /root/patches/openmw/navmeshtool.patch
RUN patch -d ${HOME}/src/openmw-${OPENMW_VERSION} -p1 -t -N < /root/patches/openmw/ktx.patch
RUN patch -d ${HOME}/src/openmw-${OPENMW_VERSION} -p1 -t -N < /root/patches/openmw/bsatool-extract-dds-only.patch
RUN patch -d ${HOME}/src/openmw-${OPENMW_VERSION} -p1 -t -N < /root/patches/openmw/base-changes.patch
RUN patch -d ${HOME}/src/openmw-${OPENMW_VERSION} -p1 -t -N < /root/patches/openmw/allow-more-es-versions.patch
#RUN patch -d ${HOME}/src/openmw-${OPENMW_VERSION} -p1 -t -N < /root/patches/openmw/rumble.patch
RUN cp /root/patches/openmw/androidmain.cpp /root/src/openmw-${OPENMW_VERSION}/apps/openmw/androidmain.cpp

# sed commands
# change post processing window size for android
RUN sed -i 's/600 600/600 400/g' ${HOME}/src/openmw-${OPENMW_VERSION}/files/data/mygui/openmw_postprocessor_hud.layout

# Snells Window
RUN sed -i 's/float ior = (cameraPos.z>0.0)?(1.333\/1.0):(1.0\/1.333);/float ior = 1.333;/g' ${HOME}/src/openmw-${OPENMW_VERSION}/files/shaders/compatibility/water.frag

RUN mkdir -p ${HOME}/src/openmw-${OPENMW_VERSION}/build && cd $_ && \
    cmake .. \
        ${COMMON_CMAKE_ARGS} \
        -DBUILD_BSATOOL=1 \
        -DBUILD_NIFTEST=0 \
        -DBUILD_ESMTOOL=0 \
        -DBUILD_LAUNCHER=0 \
        -DBUILD_MWINIIMPORTER=0 \
        -DBUILD_ESSIMPORTER=0 \
        -DBUILD_OPENCS=0 \
        -DBUILD_NAVMESHTOOL=1 \
        -DBUILD_WIZARD=0 \
        -DBUILD_MYGUI_PLUGIN=0 \
        -DBUILD_BULLETOBJECTTOOL=0 \
        -DOPENMW_USE_SYSTEM_SQLITE3=OFF \
        -DOPENMW_USE_SYSTEM_YAML_CPP=OFF \
        -DOPENMW_USE_SYSTEM_ICU=ON \
        -DOPENGL_gl_LIBRARY=${PREFIX}/lib/libng_gl4es.so \
        -DOPENGL_glx_LIBRARY=${PREFIX}/lib/libng_gl4es.so \
        -DOPENAL_INCLUDE_DIR=${PREFIX}/include/AL/ \
        -DBullet_INCLUDE_DIR=${PREFIX}/include/bullet/ \
        -DOSG_STATIC=TRUE \
        -DCMAKE_CXX_FLAGS=-std=gnu++20\ -I${PREFIX}/include/\ "${CXXFLAGS}" \
        -DMyGUI_LIBRARY=${PREFIX}/lib/libMyGUIEngineStatic.a && \
    make -j $(nproc)

# build kram
RUN wget -c https://github.com/Duron27/kram/archive/09bf2e9bd52f20658f032e957a75093c3be08698.tar.gz -O - | tar -xz -C $HOME/src/
RUN mkdir -p $HOME/src/kram-09bf2e9bd52f20658f032e957a75093c3be08698/build && cd $_ && \
    cmake .. \
        ${COMMON_CMAKE_ARGS} && make -j $(nproc)

COPY --chmod=0755 payload /root/payload

# Prepare Launcher
RUN mkdir -p /root/payload/app/src/main/jniLibs/${ABI}/

# copy over kram
RUN cp /root/src/kram-09bf2e9bd52f20658f032e957a75093c3be08698/build/kramc/kram /root/payload/app/src/main/jniLibs/${ABI}/libkram.so

# libopenmw.so is a special cases
RUN cp /root/src/openmw-${OPENMW_VERSION}/build/libopenmw.so /root/payload/app/src/main/jniLibs/${ABI}/
RUN cp /root/src/openmw-${OPENMW_VERSION}/build/bsatool /root/payload/app/src/main/jniLibs/${ABI}/libbsatool.so
RUN cp /root/src/openmw-${OPENMW_VERSION}/build/libopenmw-navmeshtool.so /root/payload/app/src/main/jniLibs/${ABI}/libnavmesh.so

# uqm and dethrace libraries are a special cases
RUN cp /root/src/UQM-MegaMod/Android/libUrQuanMasters.so /root/payload/app/src/main/jniLibs/${ABI}/libuqm.so
RUN cp /root/src/dethrace/build/src/DETHRACE/libdethrace.so /root/payload/app/src/main/jniLibs/${ABI}/

# copy over libs we compiled
RUN cp ${PREFIX}/lib/lib{openal,SDL2,ng_gl4es,collada-dom2.5-dp,delta_plugin,S3LightFixes}.so /root/payload/app/src/main/jniLibs/${ABI}/

# copy over libc++_shared
RUN find ${TOOLCHAIN}/sysroot/usr/lib/${NDK_TRIPLET} -iname "libc++_shared.so" -exec cp "{}" /root/payload/app/src/main/jniLibs/${ABI}/ \;
ENV DST=/root/payload/app/src/main/assets/libopenmw/
ENV SRC=/root/src/openmw-${OPENMW_VERSION}/build/
RUN mkdir -p ${DST}/{openmw,resources,ui}

# Copy over Resources
RUN cp -r "${SRC}/resources" "${DST}"

# Copy over UI
RUN cp -r "${HOME}/payload/app/ui" "${DST}"

# Global Config
RUN cp "${SRC}/defaults.bin" "${DST}/openmw/"
RUN cp "${SRC}/gamecontrollerdb.txt" "${DST}/openmw/"
RUN cp "${HOME}/payload/app/settings.fallback.cfg" "${DST}/openmw/"
RUN cp "${HOME}/payload/app/openmw.cfg" "${DST}/openmw/"
ARG APP_VERSION=Alpha
RUN BUILD_ID=$(find "${DST}" -type f ! -path "${DST}/resources/version" -print0 | sort -z | xargs -0 sha256sum | sha256sum | cut -c1-16) && \
    BUILD_VERSION="${APP_VERSION}-${BUILD_ID}" && \
    printf '%s\n' "${BUILD_VERSION}" >> "${DST}/resources/version" && \
    printf '%s\n' "${BUILD_VERSION}" > /root/resource-build-version.txt && \
    sed -i "s/val RANDOM_NUM = \".*\"/val RANDOM_NUM = \"${BUILD_VERSION}\"/" /root/payload/app/src/main/java/org/openmw/utils/ManageAssets.kt
RUN sed -i "s/ndkVersion = \".*\"/ndkVersion = \"$(grep 'Pkg.Revision' /root/Android/android-ndk-${NDK_VERSION}/source.properties | cut -d'=' -f2 | tr -d '[:space:]')\"/g" /root/payload/app/build.gradle.kts && \
    echo "NDK Version set to: $(grep 'Pkg.Revision' /root/Android/android-ndk-${NDK_VERSION}/source.properties | cut -d'=' -f2 | tr -d '[:space:]')"

# licensing info
RUN cp "/root/payload/3rdparty-licenses.txt" "${DST}"

# copy angle libs
COPY --chmod=0755 angle /root/angle
RUN cp /root/angle/*.so /root/payload/app/src/main/jniLibs/${ABI}/

# Remove Debug Symbols
RUN llvm-strip /root/payload/app/src/main/jniLibs/arm64-v8a/*.so

# Create Package for external Editing
RUN zip -r New_Launcher.zip /root/payload

# Build the APK!
RUN cd /root/payload/ && ./gradlew --no-daemon --max-workers=2 testDebugUnitTest assembleDebug

RUN cp /root/payload/app/build/outputs/apk/debug/*.apk openmw-android.apk

ARG SOURCE_TREE_SHA256=not-provided
RUN { \
      printf 'OpenMW Android build provenance\n'; \
      printf 'Historical base snapshot commit: 816dc6da13479adbf85a55a2f590d7ccc767b9c3\n'; \
      printf 'Fedora base image: fedora:43@sha256:a651ddf48ea28a06ed4e1e6519f51c9f47e7a5a138722ade87369b8fbb7e5b42\n'; \
      printf 'OpenMW commit: %s\n' "${OPENMW_VERSION}"; \
      printf 'OpenSceneGraph commit: %s\n' "${OSG_VERSION}"; \
      printf 'GL4ES commit: %s\n' "${GL4ES_COMMIT}"; \
      printf 'bzip2 commit: %s\n' "${BZIP2_COMMIT}"; \
      printf 'UQM-MegaMod commit: %s\n' "${UQM_COMMIT}"; \
      printf 'dethrace commit: %s\n' "${DETHRACE_COMMIT}"; \
      printf 'S3LightFixes commit: %s\n' "${S3LIGHTFIXES_COMMIT}"; \
      printf 'delta-plugin commit: 9c40703c7481635ebfc0933941e88c36a8c8b282\n'; \
      printf 'Android NDK revision: %s\n' "${NDK_REVISION}"; \
      printf 'Rust stable version: %s\n' "${RUST_STABLE_VERSION}"; \
      printf 'Rust nightly: %s\n' "${RUST_NIGHTLY}"; \
      printf 'APP_VERSION: %s\n' "${APP_VERSION}"; \
      printf 'Source tree SHA-256: %s\n' "${SOURCE_TREE_SHA256}"; \
      printf 'Resource build version: %s\n' "$(cat /root/resource-build-version.txt)"; \
      printf 'Android ABI: %s\n' "${ABI}"; \
      printf 'Android API: %s\n' "${API}"; \
      printf 'Installed RPM packages (NEVRA):\n'; \
      rpm -qa --qf '%{NEVRA}\n' | sort; \
    } > /root/build-manifest.txt

FROM scratch AS export
COPY --from=build /openmw-android.apk /openmw-android.apk
COPY --from=build /root/build-manifest.txt /build-manifest.txt
