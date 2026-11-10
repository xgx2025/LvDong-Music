package utils.hummingEcognition;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import utils.ConfigUtil;



public class qbh {
	private static final Logger log = LoggerFactory.getLogger(qbh.class);

		// webapi接口地址
		private static final String WEBSING_URL = "https://webqbh.xfyun.cn/v1/service/v1/qbh";
		// 应用ID（从 application.properties / 环境变量 QBH_APPID 读取，禁止硬编码）
		private static final String APPID = ConfigUtil.get("QBH_APPID", "qbh.appid", "");
		// 接口密钥（从 application.properties / 环境变量 QBH_API_KEY 读取，禁止硬编码）
		private static final String API_KEY = ConfigUtil.get("QBH_API_KEY", "qbh.api.key", "");
		
		private static final String ENGINE_TYPE = "afs";
		

		private static final String AUE = "raw";
		
		/*客户端传输一个audio_url参数时，Http Request Body需为空*/
		// private static final String AUDIO_URL = "http://cdn.inyuapp.com/audios/20180524120030bEAwdc.wav";
		
		private static final String AUDIO_PATH = "src/main/resources/原唱.wav";
		/**
		 * 合成 WebAPI 调用示例程序
		 * 
		 * @param args
		 * @throws IOException
		 */
		public static void main(String[] args) throws IOException {
			Map<String, String> header = buildHttpHeader();
			
			byte[] audioByteArray = FileUtil.read(AUDIO_PATH);
			String result = HttpUtil.doPost(WEBSING_URL, header, audioByteArray);
			log.info("WebAPI 接口调用结果: {}", result);
		}

		/**
		 * 组装http请求头
		 */
		private static Map<String, String> buildHttpHeader() throws UnsupportedEncodingException {
			String curTime = System.currentTimeMillis() / 1000L + "";
			// 如果使用audio_url传输音频，须在param中添加audio_url参数
			String param = "{\"aue\":\"" + AUE + "\",\"engine_type\":\"" + ENGINE_TYPE + "\"}";
			String paramBase64 = new String(Base64.encodeBase64(param.getBytes("UTF-8")));
			String checkSum = DigestUtils.md5Hex(API_KEY + curTime + paramBase64);
			Map<String, String> header = new HashMap<String, String>();
			header.put("Content-Type", "application/x-www-form-urlencoded; charset=utf-8");
			header.put("X-Param", paramBase64);
			header.put("X-CurTime", curTime);
			header.put("X-CheckSum", checkSum);
			header.put("X-Appid", APPID);
			return header;
		}
}
