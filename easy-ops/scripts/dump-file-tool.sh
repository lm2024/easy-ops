#!/bin/bash
# dump 文件压缩/分割工具
# 用于处理大体积的 .hprof 和 .core 文件

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

# 显示帮助
show_help() {
    echo -e "${GREEN}Dump 文件压缩/分割工具${NC}"
    echo ""
    echo "用法: $0 [选项] <文件路径>"
    echo ""
    echo "选项:"
    echo "  -c, --compress    压缩文件（推荐，可减少 60-80% 体积）"
    echo "  -s, --split       分割文件（默认每块 100MB）"
    echo "  -h, --help        显示此帮助信息"
    echo ""
    echo "示例:"
    echo "  $0 -c dump.hprof           # 压缩文件"
    echo "  $0 -s dump.hprof           # 分割文件"
    echo "  $0 -c -s dump.hprof        # 先压缩再分割"
    echo ""
    echo "压缩后的文件:"
    echo "  原文件名.hprof.gz          # gzip 压缩（约 20-40% 原大小）"
    echo ""
    echo "分割后的文件:"
    echo "  原文件名.hprof.001         # 第 1 块"
    echo "  原文件名.hprof.002         # 第 2 块"
    echo "  ..."
    echo ""
    echo "还原命令:"
    echo "  gunzip dump.hprof.gz       # 解压"
    echo "  cat dump.hprof.0* > dump.hprof  # 合并分割文件"
}

# 压缩文件
compress_file() {
    local file="$1"

    if [ ! -f "$file" ]; then
        echo -e "${RED}错误: 文件不存在: $file${NC}"
        exit 1
    fi

    local original_size=$(du -h "$file" | cut -f1)
    echo -e "${YELLOW}正在压缩: $file${NC}"
    echo "  原始大小: $original_size"

    # 使用 gzip 压缩
    gzip -k "$file"

    local compressed_size=$(du -h "${file}.gz" | cut -f1)
    echo -e "${GREEN}压缩完成!${NC}"
    echo "  压缩后: ${file}.gz"
    echo "  压缩后大小: $compressed_size"
    echo ""
    echo "上传时请上传 ${file}.gz 文件"
}

# 分割文件
split_file() {
    local file="$1"
    local chunk_size="${2:-100M}"  # 默认 100MB

    if [ ! -f "$file" ]; then
        echo -e "${RED}错误: 文件不存在: $file${NC}"
        exit 1
    fi

    local original_size=$(du -h "$file" | cut -f1)
    echo -e "${YELLOW}正在分割: $file${NC}"
    echo "  原始大小: $original_size"
    echo "  分割块大小: $chunk_size"

    # 使用 split 分割
    split -b "$chunk_size" -d -a 3 "$file" "${file}."

    local chunk_count=$(ls ${file}.* 2>/dev/null | wc -l)
    echo -e "${GREEN}分割完成!${NC}"
    echo "  分割块数量: $chunk_count"
    echo "  文件列表:"
    ls -lh ${file}.* | awk '{print "    " $NF " (" $5 ")"}'
    echo ""
    echo "还原命令:"
    echo "  cat ${file}.* > $file"
}

# 检查文件大小并建议处理方式
check_file_size() {
    local file="$1"
    local size_bytes=$(stat -f%z "$file" 2>/dev/null || stat -c%s "$file" 2>/dev/null)
    local size_mb=$((size_bytes / 1024 / 1024))

    if [ $size_mb -gt 500 ]; then
        echo -e "${YELLOW}警告: 文件较大 (${size_mb}MB)${NC}"
        echo "建议先压缩再上传，可减少 60-80% 体积"
        echo ""
        read -p "是否自动压缩? (y/n) " -n 1 -r
        echo
        if [[ $REPLY =~ ^[Yy]$ ]]; then
            compress_file "$file"
            return 0
        fi
    fi
    return 1
}

# 主函数
main() {
    local compress=false
    local split=false
    local file=""

    # 解析参数
    while [[ $# -gt 0 ]]; do
        case $1 in
            -c|--compress)
                compress=true
                shift
                ;;
            -s|--split)
                split=true
                shift
                ;;
            -h|--help)
                show_help
                exit 0
                ;;
            -*)
                echo -e "${RED}未知选项: $1${NC}"
                show_help
                exit 1
                ;;
            *)
                file="$1"
                shift
                ;;
        esac
    done

    # 检查是否提供了文件
    if [ -z "$file" ]; then
        echo -e "${RED}错误: 请指定文件路径${NC}"
        show_help
        exit 1
    fi

    # 检查文件是否存在
    if [ ! -f "$file" ]; then
        echo -e "${RED}错误: 文件不存在: $file${NC}"
        exit 1
    fi

    # 显示文件信息
    echo -e "${GREEN}文件信息:${NC}"
    ls -lh "$file"
    echo ""

    # 如果没有指定操作，先检查文件大小
    if [ "$compress" = false ] && [ "$split" = false ]; then
        if check_file_size "$file"; then
            exit 0
        else
            echo "文件大小正常，可以上传"
            echo ""
            echo "如果需要压缩或分割，请使用 -c 或 -s 选项"
            exit 0
        fi
    fi

    # 执行压缩
    if [ "$compress" = true ]; then
        compress_file "$file"
    fi

    # 执行分割
    if [ "$split" = true ]; then
        if [ "$compress" = true ]; then
            # 如果先压缩了，分割压缩后的文件
            split_file "${file}.gz"
        else
            split_file "$file"
        fi
    fi
}

# 运行主函数
main "$@"
