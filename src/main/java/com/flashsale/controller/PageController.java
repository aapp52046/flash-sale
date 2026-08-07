package com.flashsale.controller;

import com.flashsale.entity.FlashSaleProduct;
import com.flashsale.security.SecurityUtil;
import com.flashsale.service.FlashProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
@RequiredArgsConstructor
public class PageController {

    private final FlashProductService flashProductService;
    private final SecurityUtil securityUtil;

    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    @GetMapping("/register")
    public String registerPage() {
        return "register";
    }

    @GetMapping("/flash")
    public String flashList(Model model) {
        String username = securityUtil.getCurrentUsername();
        if (username == null) return "redirect:/login";
        model.addAttribute("username", username);
        model.addAttribute("flashProducts", flashProductService.getActiveFlashProducts());
        return "flash-list";
    }

    @GetMapping("/flash/{id}")
    public String flashDetail(@PathVariable Long id, Model model) {
        String username = securityUtil.getCurrentUsername();
        if (username == null) return "redirect:/login";
        FlashSaleProduct flash = flashProductService.getById(id);
        model.addAttribute("username", username);
        model.addAttribute("flashProduct", flash);
        return "flash-detail";
    }

    @GetMapping("/orders")
    public String myOrders(Model model) {
        String username = securityUtil.getCurrentUsername();
        if (username == null) return "redirect:/login";
        model.addAttribute("username", username);
        return "my-orders";
    }

    @GetMapping("/")
    public String home() {
        Long userId = securityUtil.getCurrentUserId();
        if (userId == null) return "redirect:/login";
        return "redirect:/flash";
    }
}
