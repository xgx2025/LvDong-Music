class AudioChunkUploader {
    constructor(options) {
        this.file = options.file;                // 音频文件
        this.chunkSize = options.chunkSize || 5 * 1024 * 1024; // 分片大小，默认5MB
        this.uploadUrl = options.uploadUrl;      // 上传接口地址
        this.fileId = options.fileId || this.generateFileId(); // 文件唯一标识
        this.totalChunks = 0;                    // 总分片数
        this.chunks = [];                        // 分片列表
        this.uploadProgress = 0;                 // 上传进度
        this.abortController = null;             // 用于中断上传
    }

    // 生成文件唯一标识（基于文件名、大小和最后修改时间）
    generateFileId() {
        return `${this.file.name}-${this.file.size}-${this.file.lastModified}`;
    }

    // 分割文件为分片
    splitFile() {
        this.totalChunks = Math.ceil(this.file.size / this.chunkSize);
        this.chunks = [];

        for (let i = 0; i < this.totalChunks; i++) {
            const start = i * this.chunkSize;
            const end = Math.min(start + this.chunkSize, this.file.size);
            const chunk = this.file.slice(start, end);

            this.chunks.push({
                index: i,
                start,
                end,
                size: end - start,
                blob: chunk
            });
        }

        return this.chunks;
    }

    // 上传单个分片
    async uploadChunk(chunk) {
        const formData = new FormData();

        // 添加分片信息
        formData.append('chunk', chunk.blob);
        formData.append('fileId', this.fileId);
        formData.append('fileName', this.file.name);
        formData.append('chunkIndex', chunk.index);
        formData.append('totalChunks', this.totalChunks);
        formData.append('fileType', this.file.type);

        this.abortController = new AbortController();

        try {
            const response = await fetch(this.uploadUrl, {
                method: 'POST',
                body: formData,
                signal: this.abortController.signal,
                headers: {
                    // 可添加认证信息
                    // 'Authorization': `Bearer ${token}`
                }
            });

            const result = await response.json();

            if (!response.ok) {
                throw new Error(result.message || `上传分片 ${chunk.index} 失败`);
            }

            return {
                success: true,
                chunkIndex: chunk.index
            };
        } catch (error) {
            if (error.name !== 'AbortError') {
                console.error(`分片 ${chunk.index} 上传失败:`, error);
            }
            return {
                success: false,
                chunkIndex: chunk.index,
                error: error.message
            };
        }
    }

    // 批量上传所有分片
    async uploadAllChunks(onProgress) {
        this.splitFile();
        let uploadedChunks = 0;

        // 依次上传分片（也可并发上传，需控制并发数）
        for (const chunk of this.chunks) {
            const result = await this.uploadChunk(chunk);

            if (!result.success) {
                // 可实现重试逻辑
                throw new Error(`分片 ${chunk.index} 上传失败，无法继续`);
            }

            uploadedChunks++;
            this.uploadProgress = Math.floor((uploadedChunks / this.totalChunks) * 100);

            // 触发进度回调
            if (onProgress) {
                onProgress(this.uploadProgress, chunk.index);
            }
        }

        // 所有分片上传完成，通知后端合并
        return this.notifyMerge();
    }

    // 通知后端合并分片
    async notifyMerge() {
        try {
            const response = await fetch(`${this.uploadUrl}?action=merge`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    // 'Authorization': `Bearer ${token}`
                },
                body: JSON.stringify({
                    fileId: this.fileId,
                    fileName: this.file.name,
                    totalChunks: this.totalChunks,
                    fileType: this.file.type
                })
            });

            const result = await response.json();

            if (!response.ok) {
                throw new Error(result.message || '文件合并失败');
            }

            return {
                success: true,
                data: result.data
            };
        } catch (error) {
            console.error('合并文件失败:', error);
            return {
                success: false,
                error: error.message
            };
        }
    }

    // 检查已上传的分片（用于断点续传）
    async checkUploadedChunks() {
        try {
            const response = await fetch(`${this.uploadUrl}?action=check&fileId=${this.fileId}`);
            const result = await response.json();

            if (response.ok && result.success) {
                return result.uploadedChunks || [];
            }
        } catch (error) {
            console.error('检查已上传分片失败:', error);
        }

        return [];
    }

    // 中断上传
    abort() {
        if (this.abortController) {
            this.abortController.abort();
            console.log('上传已中断');
        }
    }
}

// 使用示例
document.getElementById('audioFileInput').addEventListener('change', async (e) => {
    const file = e.target.files[0];
    if (!file || !file.type.startsWith('audio/')) {
        alert('请选择音频文件');
        return;
    }

    const uploader = new AudioChunkUploader({
        file: file,
        uploadUrl: '/api/upload/audio',
        chunkSize: 5 * 1024 * 1024 // 5MB每片
    });

    // 上传进度回调
    const progressCallback = (progress, chunkIndex) => {
        console.log(`上传进度: ${progress}%，当前分片: ${chunkIndex}`);
        document.getElementById('progressBar').style.width = `${progress}%`;
        document.getElementById('progressText').textContent = `上传中: ${progress}%`;
    };

    try {
        // 断点续传：先检查已上传的分片
        const uploadedChunks = await uploader.checkUploadedChunks();
        if (uploadedChunks.length > 0) {
            console.log(`已上传 ${uploadedChunks.length} 个分片，将继续上传剩余部分`);
        }

        // 开始上传所有分片
        const result = await uploader.uploadAllChunks(progressCallback);

        if (result.success) {
            document.getElementById('progressText').textContent = '上传完成!';
            console.log('文件上传成功:', result.data);
        }
    } catch (error) {
        document.getElementById('progressText').textContent = `上传失败: ${error.message}`;
    }
});
