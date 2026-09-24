package com.tindev.integration;

import com.tindev.domain.InventoryMovementType;
import com.tindev.domain.UserRole;
import com.tindev.exceptions.ApiException;
import com.tindev.modal.Branch;
import com.tindev.modal.Category;
import com.tindev.modal.Inventory;
import com.tindev.modal.Product;
import com.tindev.modal.Store;
import com.tindev.modal.User;
import com.tindev.repository.BranchRepository;
import com.tindev.repository.CategoryRepository;
import com.tindev.repository.InventoryMovementRepository;
import com.tindev.repository.InventoryRepository;
import com.tindev.repository.ProductRepository;
import com.tindev.repository.StoreRepository;
import com.tindev.repository.UserRepository;
import com.tindev.service.InventoryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Uses MySQL rather than H2 because this test verifies the PESSIMISTIC_WRITE
 * behavior that prevents two sales from consuming the same final unit.
 */
@SpringBootTest
@ActiveProfiles("testcontainers")
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MysqlInventoryConcurrencyIT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("sass_pos_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired private InventoryService inventoryService;
    @Autowired private InventoryRepository inventoryRepository;
    @Autowired private InventoryMovementRepository inventoryMovementRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private BranchRepository branchRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private StoreRepository storeRepository;

    @AfterEach
    void clearDatabase() {
        inventoryMovementRepository.deleteAll();
        inventoryRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        branchRepository.deleteAll();
        userRepository.deleteAll();
        storeRepository.deleteAll();
    }

    @Test
    void onlyOneConcurrentSaleCanConsumeTheLastUnit() throws Exception {
        Fixture fixture = fixtureWithOneUnit();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Boolean> first = pool.submit(() -> sellOne(fixture, ready, start));
            Future<Boolean> second = pool.submit(() -> sellOne(fixture, ready, start));
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();

            boolean firstSucceeded = first.get(15, TimeUnit.SECONDS);
            boolean secondSucceeded = second.get(15, TimeUnit.SECONDS);
            assertEquals(1, (firstSucceeded ? 1 : 0) + (secondSucceeded ? 1 : 0));

            Inventory inventory = inventoryRepository.findById(fixture.inventoryId()).orElseThrow();
            assertEquals(0, inventory.getQuantity());
            assertEquals(1, inventoryMovementRepository.findByBranchIdOrderByCreatedAtDesc(fixture.branchId()).size());
        } finally {
            pool.shutdownNow();
        }
    }

    private boolean sellOne(Fixture fixture, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Concurrent test did not start");
        try {
            User actor = userRepository.getReferenceById(fixture.actorId());
            inventoryService.adjustInventoryForTransaction(fixture.productId(), fixture.branchId(), -1,
                    InventoryMovementType.SALE, "MySQL concurrency test", actor);
            return true;
        } catch (ApiException exception) {
            assertEquals(409, exception.status().value());
            return false;
        }
    }

    private Fixture fixtureWithOneUnit() {
        User owner = new User();
        owner.setFullName("Test owner");
        owner.setEmail("owner-concurrency@example.test");
        owner.setPassword("not-used");
        owner.setRole(UserRole.ROLE_STORE_ADMIN);
        owner = userRepository.save(owner);

        Store store = new Store();
        store.setBrand("Concurrency store");
        store.setStoreAdmin(owner);
        store = storeRepository.save(store);
        owner.setStore(store);
        userRepository.save(owner);

        Branch branch = new Branch();
        branch.setStore(store);
        branch.setName("Concurrency branch");
        branch.setAddress("Test address");
        branch.setPhone("0900000000");
        branch.setWorkingDays(List.of("Monday"));
        branch.setOpenTime(LocalTime.of(8, 0));
        branch.setCloseTime(LocalTime.of(20, 0));
        branch = branchRepository.save(branch);

        Category category = new Category();
        category.setName("Concurrency category");
        category.setStore(store);
        category = categoryRepository.save(category);

        Product product = new Product();
        product.setStore(store);
        product.setCategory(category);
        product.setName("Concurrency product");
        product.setSku("CONCURRENCY-ONE-UNIT");
        product.setMrp(BigDecimal.TEN);
        product.setSellingPrice(BigDecimal.TEN);
        product = productRepository.save(product);

        Inventory inventory = new Inventory();
        inventory.setBranch(branch);
        inventory.setProduct(product);
        inventory.setQuantity(1);
        inventory = inventoryRepository.saveAndFlush(inventory);
        return new Fixture(inventory.getId(), product.getId(), branch.getId(), owner.getId());
    }

    private record Fixture(Long inventoryId, Long productId, Long branchId, Long actorId) { }
}
