import logger from '../logger.js';
const LIVE2D_CONTEXT_OPTIONS = { premultipliedAlpha: true, preserveDrawingBuffer: false };
function getLive2dContext(canvas) {
    if (canvas.__live2dGl && !canvas.__live2dGl.isContextLost?.()) {
        return canvas.__live2dGl;
    }
    const gl = canvas.getContext('webgl2', LIVE2D_CONTEXT_OPTIONS) || canvas.getContext('webgl', LIVE2D_CONTEXT_OPTIONS) || canvas.getContext('experimental-webgl', LIVE2D_CONTEXT_OPTIONS);
    if (gl) {
        canvas.__live2dGl = gl;
    }
    return gl;
}
class PlatformManager {
    constructor() {
        this.cache = {};
    }
    loadBytes(path, callback) {
        if (path in this.cache) {
            return callback(this.cache[path]);
        }
        fetch(path)
            .then(response => response.arrayBuffer())
            .then(arrayBuffer => {
            this.cache[path] = arrayBuffer;
            callback(arrayBuffer);
        });
    }
    loadLive2DModel(path, callback) {
        let model = null;
        this.loadBytes(path, buf => {
            model = Live2DModelWebGL.loadModel(buf);
            callback(model);
        });
    }
    loadTexture(model, no, path, callback) {
        const loadedImage = new Image();
        loadedImage.crossOrigin = 'anonymous';
        loadedImage.src = path;
        loadedImage.onload = () => {
            const canvas = document.getElementById('live2d');
            const gl = getLive2dContext(canvas);
            if (!gl) {
                logger.error('Failed to create WebGL context for texture.');
                return -1;
            }
            let texture = gl.createTexture();
            if (!texture) {
                logger.error('Failed to generate gl texture name.');
                return -1;
            }
            if (model.isPremultipliedAlpha() == false) {
                gl.pixelStorei(gl.UNPACK_PREMULTIPLY_ALPHA_WEBGL, 1);
            }
            gl.pixelStorei(gl.UNPACK_FLIP_Y_WEBGL, 1);
            gl.activeTexture(gl.TEXTURE0);
            gl.bindTexture(gl.TEXTURE_2D, texture);
            gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, loadedImage);
            gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
            gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR_MIPMAP_NEAREST);
            gl.generateMipmap(gl.TEXTURE_2D);
            model.__live2dTextureHandles = model.__live2dTextureHandles || [];
            model.__live2dTextureHandles[no] = texture;
            model.setTexture(no, texture);
            texture = null;
            if (typeof callback == 'function')
                callback();
        };
        loadedImage.onerror = () => {
            logger.error('Failed to load image : ' + path);
        };
    }
    jsonParseFromBytes(buf) {
        let jsonStr;
        const bomCode = new Uint8Array(buf, 0, 3);
        if (bomCode[0] == 239 && bomCode[1] == 187 && bomCode[2] == 191) {
            jsonStr = String.fromCharCode.apply(null, new Uint8Array(buf, 3));
        }
        else {
            jsonStr = String.fromCharCode.apply(null, new Uint8Array(buf));
        }
        const jsonObj = JSON.parse(jsonStr);
        return jsonObj;
    }
}
export default PlatformManager;
