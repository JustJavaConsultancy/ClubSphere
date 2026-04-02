package com.justjava.mycommunity.product;

import com.justjava.mycommunity.account.AuthenticationManager;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Controller
@RequestMapping("/products")
public class ProductController {

    private final AuthenticationManager authenticationManager;
    private final List<Product> products = new ArrayList<>();
    private Long nextId = 1L;

    // Directory to store uploaded images
    private final String UPLOAD_DIR = "uploads/products/";

    public ProductController(AuthenticationManager authenticationManager) {
        this.authenticationManager = authenticationManager;
        // Create upload directory if it doesn't exist
        try {
            Files.createDirectories(Paths.get(UPLOAD_DIR));
        } catch (IOException e) {
            System.err.println("Could not create upload directory: " + e.getMessage());
        }
        initializeSampleData();
    }

    private void initializeSampleData() {
        // Add some sample data for testing
        String currentUserId = "sample-user";

        Product sampleProduct = new Product();
        sampleProduct.setId(nextId++);
        sampleProduct.setName("Sample Product");
        sampleProduct.setDescription("This is a sample product for demonstration purposes.");
        sampleProduct.setPrice(new BigDecimal("29.99"));
        sampleProduct.setUserId(currentUserId);
        sampleProduct.setCreatedAt(LocalDateTime.now());
        sampleProduct.setImageUrl("https://images.unsplash.com/photo-1556742049-0cfed4f6a45d?w=300&h=200&fit=crop");

        products.add(sampleProduct);
    }

    private String saveImage(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            return null;
        }

        try {
            // Generate unique filename
            String originalFilename = image.getOriginalFilename();
            String extension = originalFilename != null && originalFilename.contains(".")
                    ? originalFilename.substring(originalFilename.lastIndexOf("."))
                    : ".jpg";
            String filename = UUID.randomUUID().toString() + extension;

            // Save file
            Path filePath = Paths.get(UPLOAD_DIR + filename);
            Files.copy(image.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            // Return URL path (relative to web root)
            return "/uploads/products/" + filename;

        } catch (IOException e) {
            System.err.println("Error saving image: " + e.getMessage());
            // Return a placeholder image URL as fallback
            return "https://images.unsplash.com/photo-1556742049-0cfed4f6a45d?w=300&h=200&fit=crop";
        }
    }

    @GetMapping
    public String listProducts(Model model) {
        String currentUserId = (String) authenticationManager.get("sub");
        final String finalUserId = currentUserId != null ? currentUserId : "sample-user";

        List<Product> userProducts = products.stream()
                .filter(product -> product.getUserId() != null && product.getUserId().equals(finalUserId))
                .toList();

        model.addAttribute("products", userProducts);
        model.addAttribute("currentPath", "/products");
        return "products";
    }

    @GetMapping("/add")
    public String showAddProductForm(Model model) {
        model.addAttribute("currentPath", "/products/add");
        return "add-product";
    }

    @PostMapping("/add")
    public String addProduct(
            @RequestParam("name") String name,
            @RequestParam("description") String description,
            @RequestParam("price") BigDecimal price,
            @RequestParam(value = "image", required = false) MultipartFile image,
            Model model) {

        String currentUserId = (String) authenticationManager.get("sub");
        final String finalUserId = currentUserId != null ? currentUserId : "sample-user";

        Product product = new Product();
        product.setId(nextId++);
        product.setName(name);
        product.setDescription(description);
        product.setPrice(price);
        product.setUserId(finalUserId);
        product.setCreatedAt(LocalDateTime.now());

        // Handle image upload
        String imageUrl = saveImage(image);
        product.setImageUrl(imageUrl);

        products.add(product);

        return "redirect:/products";
    }

    @GetMapping("/edit/{id}")
    public String showEditProductForm(@PathVariable Long id, Model model) {
        Optional<Product> product = products.stream()
                .filter(p -> p.getId().equals(id))
                .findFirst();

        if (product.isPresent()) {
            model.addAttribute("product", product.get());
            model.addAttribute("currentPath", "/products/edit/" + id);
            return "edit-product";
        }

        return "redirect:/products";
    }

    @PostMapping("/edit/{id}")
    public String editProduct(
            @PathVariable Long id,
            @RequestParam("name") String name,
            @RequestParam("description") String description,
            @RequestParam("price") BigDecimal price,
            @RequestParam(value = "image", required = false) MultipartFile image,
            @RequestParam(value = "removeImage", required = false) boolean removeImage,
            Model model) {

        Optional<Product> productOpt = products.stream()
                .filter(p -> p.getId().equals(id))
                .findFirst();

        if (productOpt.isPresent()) {
            Product product = productOpt.get();
            product.setName(name);
            product.setDescription(description);
            product.setPrice(price);

            if (removeImage) {
                product.setImageUrl(null);
            } else if (image != null && !image.isEmpty()) {
                // Save new image and update URL
                String imageUrl = saveImage(image);
                product.setImageUrl(imageUrl);
            }
            // If neither removeImage nor new image, keep existing image URL
        }

        return "redirect:/products";
    }

    @PostMapping("/delete/{id}")
    public String deleteProduct(@PathVariable Long id) {
        products.removeIf(product -> product.getId().equals(id));
        return "redirect:/products";
    }

    @GetMapping("/view/{id}")
    @ResponseBody
    public Product viewProduct(@PathVariable Long id) {
        return products.stream()
                .filter(p -> p.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    // Mobile routes
    @GetMapping("/mobile")
    public String listProductsMobile(Model model) {
        String currentUserId = (String) authenticationManager.get("sub");
        final String finalUserId = currentUserId != null ? currentUserId : "sample-user";

        List<Product> userProducts = products.stream()
                .filter(product -> product.getUserId() != null && product.getUserId().equals(finalUserId))
                .toList();

        model.addAttribute("products", userProducts);
        model.addAttribute("currentPath", "/products/mobile");
        return "mobile-products";
    }

    @PostMapping("/mobile/add")
    public String addProductMobile(
            @RequestParam("name") String name,
            @RequestParam("description") String description,
            @RequestParam("price") BigDecimal price,
            @RequestParam(value = "image", required = false) MultipartFile image,
            Model model) {

        String currentUserId = (String) authenticationManager.get("sub");
        final String finalUserId = currentUserId != null ? currentUserId : "sample-user";

        Product product = new Product();
        product.setId(nextId++);
        product.setName(name);
        product.setDescription(description);
        product.setPrice(price);
        product.setUserId(finalUserId);
        product.setCreatedAt(LocalDateTime.now());

        // Handle image upload
        String imageUrl = saveImage(image);
        product.setImageUrl(imageUrl);

        products.add(product);

        return "redirect:/products/mobile";
    }

    @GetMapping("/mobile/add")
    public String showAddProductFormMobile(Model model) {
        model.addAttribute("currentPath", "/products/mobile/add");
        return "mobile-add-product";
    }

    @PostMapping("/mobile/edit/{id}")
    public String editProductMobile(
            @PathVariable Long id,
            @RequestParam("name") String name,
            @RequestParam("description") String description,
            @RequestParam("price") BigDecimal price,
            @RequestParam(value = "image", required = false) MultipartFile image,
            @RequestParam(value = "removeImage", required = false) boolean removeImage,
            Model model) {

        Optional<Product> productOpt = products.stream()
                .filter(p -> p.getId().equals(id))
                .findFirst();

        if (productOpt.isPresent()) {
            Product product = productOpt.get();
            product.setName(name);
            product.setDescription(description);
            product.setPrice(price);

            if (removeImage) {
                product.setImageUrl(null);
            } else if (image != null && !image.isEmpty()) {
                // Save new image and update URL
                String imageUrl = saveImage(image);
                product.setImageUrl(imageUrl);
            }
        }

        return "redirect:/products/mobile";
    }

    @GetMapping("/mobile/edit/{id}")
    public String showEditProductFormMobile(@PathVariable Long id, Model model) {
        Optional<Product> product = products.stream()
                .filter(p -> p.getId().equals(id))
                .findFirst();

        if (product.isPresent()) {
            model.addAttribute("product", product.get());
            model.addAttribute("currentPath", "/products/mobile/edit/" + id);
            return "mobile-edit-product";
        }

        return "redirect:/products/mobile";
    }

    @PostMapping("/mobile/delete/{id}")
    public String deleteProductMobile(@PathVariable Long id) {
        products.removeIf(product -> product.getId().equals(id));
        return "redirect:/products/mobile";
    }

    // Product entity class
    public static class Product {
        private Long id;
        private String name;
        private String description;
        private BigDecimal price;
        private String imageUrl;
        private String userId;
        private LocalDateTime createdAt;

        // Getters and setters
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public BigDecimal getPrice() { return price; }
        public void setPrice(BigDecimal price) { this.price = price; }

        public String getImageUrl() { return imageUrl; }
        public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }

        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    }
}
