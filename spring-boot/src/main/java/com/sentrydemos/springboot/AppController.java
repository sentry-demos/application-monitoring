package com.sentrydemos.springboot;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.sentry.IHub;
import io.sentry.ISpan;
import io.sentry.ITransaction;
import io.sentry.Sentry;
import io.sentry.SpanStatus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
public class AppController {

	private final Logger logger = LoggerFactory.getLogger(Application.class);
	private List<String> headerTags = new ArrayList<>();
	
	@Autowired
	private DatabaseHelper dbHelper = new DatabaseHelper();

	@Autowired
	private IHub hub;
	
	// headers passed by frontend
	@Bean
	public void initHeaders() {
		headerTags.add("se");
		headerTags.add("customerType");
		headerTags.add("email");

	}

	private void setTags(HttpServletRequest request) {
		//to print all headers
		/*
		 * logger.info("request header names and vals: "); for (Enumeration<?> e =
		 * request.getHeaderNames(); e.hasMoreElements();) { String nextHeaderName =
		 * (String) e.nextElement(); String headerValue =
		 * request.getHeader(nextHeaderName); logger.info("\t" + nextHeaderName + ", " +
		 * headerValue); }
		 */

		for (String tag : headerTags) {
			logger.info("Checking header for tag: " + tag);
			String header = request.getHeader(tag);
			if (header != null && header != "null") {
				logger.info("Setting " + tag + " Sentry tag as " + header);
				Sentry.setTag(tag, header);
			}
		}

	}
	
	@CrossOrigin
	@GetMapping("/")
	public String index() {
		logger.info("returning from / call: Greetings from Spring Boot!");
		return "Greetings from Spring Boot!";

	}

	@GetMapping("/handled")
	public String HandledError() {
		String someLocalVariable = "stack locals";

		try {
			int example = 1 / 0;
		} catch (Exception e) {
			logger.error("caught exception", e);
			return "Fail";
		}
		return "Success";
	}

	@GetMapping("/unhandled")
	public String UnhandledError() {
		throw new RuntimeException("Unhandled Exception!");
	}

	@GetMapping("/logback")
	public String Logback() {
		logger.info("info log");
		logger.warn("warn log");
		logger.error("error log");
		return "Made an info, warn, and error log entry.\n"
				+ "Whether they go to Sentry depends on the application.properties value: sentry.logging.minimum-event-level";

	}

	@CrossOrigin
	@GetMapping("/products")
	public String GetProductsDelay(HttpServletRequest request) {
		ISpan span = hub.getSpan().startChild("Overhead", "Set tags");
		//initInventory(); // initialize inventory each time we hit this endpoint
		setTags(request);
		span.finish();
		String allProducts = dbHelper.mapAllProducts(hub.getSpan());
		
		return allProducts;
	}

	@CrossOrigin
	@GetMapping("/products-join")
	public String GetProducts(HttpServletRequest request) {
		ISpan span = hub.getSpan().startChild("Overhead", "Set tags");
		setTags(request);
		span.finish();
		String allProducts = dbHelper.mapAllProductsJoin(hub.getSpan());
		return allProducts;
	}

	@CrossOrigin
	@PostMapping("/checkout")
	public String CheckoutCart(HttpServletRequest request, @RequestBody String payload) throws Exception {
		ISpan span = hub.getSpan().startChild("Overhead", "Set tags and map payload to Cart object");
		setTags(request);
		
		JSONObject json = new JSONObject(payload);
		
		ObjectMapper objectMapper = new ObjectMapper();

		Cart cart = objectMapper.readValue(json.get("cart").toString(), Cart.class);

		span.finish();
		
		ISpan checkoutSpan = hub.getSpan().startChild("Process Order", "Checkout Cart quantities");
		
		checkout(cart.getQuantities(), checkoutSpan);
		
		checkoutSpan.finish();
		return "success";
	}

	private void checkout(Map<String, Integer> quantities, ISpan span) {

		Map<String, Integer> tempInventory = dbHelper.getInventory(quantities.keySet(), span);

		ISpan inventorySpan = span.startChild("Reduce Inventory", "Reduce inventory from Cart quantities");
		for (String key : quantities.keySet()) {
			logger.info("Item " + key + " has quantity " + quantities.get(key));

			int currentInventory = tempInventory.get(key);
			currentInventory = currentInventory - quantities.get(key);
			if (currentInventory < 0) {
				String message = "No inventory for " + key;
				inventorySpan.setStatus(SpanStatus.fromHttpStatusCode(500, SpanStatus.INTERNAL_ERROR));
				inventorySpan.finish(); //resolve spans before throwing exception
				span.finish(); //resolve spans before throwing exception
				throw new RuntimeException(message);
			}

			tempInventory.put(key, currentInventory);

		}
		inventorySpan.finish();
	}

	/*
	 * BELOW ARE TEST FUNCTIONS
	 */
	@CrossOrigin
	@PostMapping("/postbody")
	public String postBody(@RequestBody String fullName) {
		return "Hello " + fullName;
	}
	
}