import Request from '@/utils/axios'


export function uploadFile(data: any): Promise<Result<FileUploadVo>> {
    return Request.post('/system/v1/file/upload', data).then((response: any) => {
        if (response && response.data) {
            const fileUploadVo: FileUploadVo = {
                id: response.data.id,
                cosId: response.data.cosId,
                fileName: response.data.fileName,
                filePath: response.data.filePath,
                fileSize: response.data.fileSize,
                fileSuffix: response.data.fileSuffix,
                fileType: response.data.fileType
            };
            return { ...response, data: fileUploadVo } as Result<FileUploadVo>;
        }
        throw new Error('Invalid response format');
    });
}

interface Result<T>{
    code: number;
    data: T;
    message: string;
}

interface FileUploadVo{
    id:string;
    cosId:string;
    fileName:string;
    filePath:string;
    fileSize:string;
    fileSuffix:string;
    fileType:number;
}