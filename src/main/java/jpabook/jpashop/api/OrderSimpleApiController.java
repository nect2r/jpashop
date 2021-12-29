package jpabook.jpashop.api;

import jpabook.jpashop.domain.Address;
import jpabook.jpashop.domain.Order;
import jpabook.jpashop.domain.OrderStatus;
import jpabook.jpashop.repository.OrderRepository;
import jpabook.jpashop.repository.OrderSearch;
import jpabook.jpashop.repository.order.simplequery.OrderSimpleQueryDto;
import jpabook.jpashop.repository.order.simplequery.OrderSimpleQueryRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

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
public class OrderSimpleApiController {

    private final OrderRepository orderRepository;
    private final OrderSimpleQueryRepository orderSimpleQueryRepository;

    /**
     * 양방향 연관관계와 Json변환 작업으로 인해서 toString으로 돌아서 반환하게되면 끝나지않는다.
     * 해결하기 위해서 양방향 Entity중 한곳에 @JsonIgnore를 넣어서 제외해준다.
     *
     * 또한 JsonIgnore로 양방향 중에 한방향을 이그노어 시키더라도
     * Lazy(지연로딩)으로 맵핑되어있는 Entity를 호출하여 Json시키는 순간
     * HttpMessageConversionException 발생함
     * 이유는 ByteBuddyInterceptor로 생성되어있는 프록시 객체때문임
     * 
     * 위에 2번 문제를 해결하기위해서 hibernate5Module를 Bean에 등록하고 요청하면 가능함
     * 하지만 Entity를 직접반환하는 경우는 적절치 못하기떄문에 사용하면 안됨
     * @return
     */
    @GetMapping("/api/v1/simple-orders")
    public List<Order> ordersV1() {
        List<Order> all = orderRepository.findAllByString(new OrderSearch());
        for (Order order : all) {
            order.getMember().getName(); //Lazy 강제 초기화
            order.getDelivery().getAddress(); //Lazy 강제 초기화
        }
        return all;
    }

    /**
     * V1에 비해서 직접 Entity를 반환하지 않기떄문에 API 스펙이 바뀔일이 없음
     * 대신 지연로딩이기때문에 SimpleOrderDto를 생성할떄마다 N + 1문제가 발생하며
     * 양이 늘어날수록 성능 문제가 심각함
     *
     * @return
     */
    @GetMapping("/api/v2/simple-orders")
    public List<SimpleOrderDto> ordersV2() {
        List<Order> orders = orderRepository.findAllByString(new OrderSearch());

        List<SimpleOrderDto> result = orders.stream()
                .map(o -> new SimpleOrderDto(o))
                .collect(Collectors.toList());

        return result;
    }

    /**
     * fetch join으로 query를 변경함
     * 이로 인해서 N+1문제는 발생하지않으며 한번의 쿼리로 리스트를 출력할수있음
     * 지연로딩으로 인한 성능문제를 쉽게 제거함
     * @return
     */
    @GetMapping("/api/v3/simple-orders")
    public List<SimpleOrderDto> ordersV3() {
        List<Order> orders = orderRepository.findAllWithMemberDelivery();

        List<SimpleOrderDto> result = orders.stream()
                .map(o -> new SimpleOrderDto(o))
                .collect(Collectors.toList());

        return result;
    }

    /**
     * V3에 비해서 필요한 값만 Query에서 불러오기떄문에 속도면에서는 이득(미비)
     * 하지만 불러오는 것만 사용하기떄문에 다양한곳에서 사용할 수 없음
     * V3는 다불러오고 Dto만 변경해서 다른 API에서도 사용할 수 가 있음
     *
     * 사용해야한다면 별도의 SimpleRepository를 사용한다
     *
     * V3와 V4의 경우가 존재하기떄문에
     * 쿼리 방식 선택 권장 순서는
     * 1. 엔티티를 DTO로 변환하는 방법 -> * 로 불러온걸 DTO로 변환
     * 2. 필요하면 페치 조인으로 성능을 최적화 -> 즉시로딩으로 지연로딩으로인한 N+1문제를 해결
     * 3. 그래도 성능이안되면 DTO로 직접 조회하는 방법 -> *의 경우 Query 속도가 느려지니 개별적으로 가져와서 해결
     * 4. 최후는 JPA가 제공하는 네이티브SQL이나 스프링JDBC를 연결하여 SQL을 직접 사용한다 -> 그것도안되면 네이티브 SQL
     * * @return
     */
    @GetMapping("/api/v4/simple-orders")
    public List<OrderSimpleQueryDto> ordersV4() {
        return orderSimpleQueryRepository.findOrderDtos();
    }

    @Data
    static class SimpleOrderDto {
        private Long orderId;
        private String name;
        private LocalDateTime orderDate;
        private OrderStatus orderStatus;
        private Address address;

        public SimpleOrderDto(Order order) {
            orderId = order.getId();
            name = order.getMember().getName();
            orderDate = order.getOrderDate();
            orderStatus = order.getStatus();
            address = order.getDelivery().getAddress();
        }
    }

}
