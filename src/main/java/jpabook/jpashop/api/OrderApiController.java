package jpabook.jpashop.api;

import jpabook.jpashop.domain.Address;
import jpabook.jpashop.domain.Order;
import jpabook.jpashop.domain.OrderItem;
import jpabook.jpashop.domain.OrderStatus;
import jpabook.jpashop.repository.OrderRepository;
import jpabook.jpashop.repository.OrderSearch;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * xToOne(ManyToOne, OneToOne)
 * Order
 * Order -> Member
 * Order -> Delivery
 */
@RestController
@RequiredArgsConstructor
public class OrderApiController {

    private final OrderRepository orderRepository;

    /**
     * 엔티티를 직접반환
     * LAZY로 인해서 for를 돌아서 강제 초기화
     * @return
     */
    @GetMapping("/api/v1/orders")
    public List<Order> ordersV1() {
        List<Order> all = orderRepository.findAllByString(new OrderSearch());
        for (Order order : all) {
            order.getMember().getName();
            order.getDelivery().getAddress();

            List<OrderItem> orderItems = order.getOrderItems();
            orderItems.stream().forEach(o -> o.getItem().getName());
        }
        return all;
    }

    /**
     * 엔티티에서 DTO로 변환하였지만
     * 1차 방법 OrderItems같은경우는 Entity 그대로 반환됨
     * 2차 방법은 OrderItems를 DTO 한번 더 변환
     * @return
     */
    @GetMapping("/api/v2/orders")
    public List<OrderDto> ordersV2() {
        List<Order> orders = orderRepository.findAllByString(new OrderSearch());
        List<OrderDto> collect = orders.stream()
                .map(o -> new OrderDto(o))
                .collect(Collectors.toList());
        return collect;
    }

    /**
     * Join으로 인하여 데이터가 늘어남에따라
     * id도 중복되어서 늘어날수 있다
     * 그경우에는 distinct를 사용하여 중복된id값을 jpa에서 줄여준다
     *
     * 단점은 페이징 불가능
     * --> 너무 치명적임임
     *
     * @return
     */
    @GetMapping("/api/v3/orders")
    public List<OrderDto> ordersV3() {
        List<Order> orders = orderRepository.findAllWithItem();

        for (Order order : orders) {
            System.out.println("order = " + order + " id =" + order.getId());
        }

        List<OrderDto> collect = orders.stream()
                .map(o -> new OrderDto(o))
                .collect(Collectors.toList());
        return collect;
    }

    /**
     * V3에 비해 지연로딩이 들어갔지만 배치사이즈를 올렸기때문에 쿼리 양이 크게 늘어나짆않음
     * 큰 장점은 페이징을 사용할 수 있음
     *
     * xToOne 관계는 fetch join으로 즉시로딩을 함으로써 성능을 최적화하고
     * xToMany 관계는 BacthSize를 올려서 지연로딩을 함으로써 페이징도 한다
     *
     * @param offset
     * @param limit
     * @return
     */
    @GetMapping("/api/v3.1/orders")
    public List<OrderDto> ordersV3_page(
            @RequestParam(value = "offset", defaultValue = "0") int offset,
            @RequestParam(value = "limit", defaultValue = "100") int limit) {

        List<Order> orders = orderRepository.findAllWithMemberDelivery(offset, limit);

        List<OrderDto> collect = orders.stream()
                .map(o -> new OrderDto(o))
                .collect(Collectors.toList());

        return collect;
    }

    @Data
    static class OrderDto {
        private Long orderId;
        private String name;
        private LocalDateTime orderDate;
        private OrderStatus orderStatus;
        private Address address;
        private List<OrderItemDto> orderItems;

        public OrderDto(Order order) {
            orderId = order.getId();
            name = order.getMember().getName();
            orderDate = order.getOrderDate();
            orderStatus = order.getStatus();
            address = order.getDelivery().getAddress();
            //1차 방법 OrderItems가 Entity 그대로 반환됨
            //order.getOrderItems().stream().forEach(o -> o.getItem().getName());
            //orderItems = order.getOrderItems();
            orderItems = order.getOrderItems().stream()
                    .map(orderItem -> new OrderItemDto(orderItem))
                    .collect(Collectors.toList());
        }
    }

    @Data
    static class OrderItemDto {
        private String itemName; //상품 명
        private int orderPrice; //주문 가격
        private int count; //주문 수량

        public OrderItemDto(OrderItem orderItem) {
            itemName = orderItem.getItem().getName();
            orderPrice = orderItem.getOrderPrice();
            count = orderItem.getCount();
        }
    }
}
