package com.gods.saas.domain.repository;

import com.gods.saas.domain.model.Appointment;
import com.gods.saas.domain.repository.projection.BarberAgendaProjection;
import com.gods.saas.domain.repository.projection.LastVisitProjection;
import com.gods.saas.domain.repository.projection.NextAppointmentProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    List<Appointment> findByTenant_IdAndBranch_IdAndFechaOrderByHoraInicioAsc(
            Long tenantId,
            Long branchId,
            LocalDate fecha
    );

    List<Appointment> findByTenant_IdAndBranch_IdAndUser_IdAndFechaOrderByHoraInicioAsc(
            Long tenantId,
            Long branchId,
            Long userId,
            LocalDate fecha
    );



    @Query(value = """
    select
        a.appointment_id as appointmentId,
        a.fecha as fecha,
        a.hora_inicio as horaInicio,
        a.hora_fin as horaFin,
        s.nombre as servicio,
        trim(coalesce(u.nombre, '') || ' ' || coalesce(u.apellido, '')) as barbero,
        b.nombre as branch,
        a.estado as estado
    from appointment a
    join service s on s.service_id = a.service_id
    left join app_user u on u.user_id = a.user_id
    join branch b on b.branch_id = a.branch_id
    where a.tenant_id = :tenantId
      and a.customer_id = :customerId
      and a.estado in ('CREATED', 'PROGRAMADA', 'PENDIENTE', 'CONFIRMED')
      and (
            a.fecha > :today
            or (a.fecha = :today and a.hora_inicio >= :nowTime)
      )
    order by a.fecha asc, a.hora_inicio asc
    limit 1
    """, nativeQuery = true)
    Optional<NextAppointmentProjection> findNextAppointment(
            @Param("tenantId") Long tenantId,
            @Param("customerId") Long customerId,
            @Param("today") LocalDate today,
            @Param("nowTime") LocalTime nowTime
    );

    @Query(value = """
    select
        sa.sale_id as appointmentId,
        sa.fecha_creacion::date as fecha,
        coalesce(
            string_agg(distinct s.nombre, ', '),
            'Servicio'
        ) as servicio,
        coalesce(sum(distinct lm.puntos), 0) as puntos,
        coalesce(sum(si.subtotal), 0) as total
    from sale sa
    left join sale_item si
           on si.sale_id = sa.sale_id
    left join service s
           on s.service_id = si.service_id
    left join loyalty_movement lm
           on lm.referencia_id = sa.sale_id
          and lm.tipo = 'EARN'
          and lm.origen = 'SALE'
          and lm.tenant_id = sa.tenant_id
          and lm.customer_id = sa.customer_id
    where sa.tenant_id = :tenantId
      and sa.customer_id = :customerId
    group by sa.sale_id, sa.fecha_creacion
    order by sa.fecha_creacion desc
    limit :limit
""", nativeQuery = true)
    List<LastVisitProjection> findLastVisits(
            @Param("tenantId") Long tenantId,
            @Param("customerId") Long customerId,
            @Param("limit") int limit
    );

    @Query(value = """
    select distinct date_trunc('month', a.fecha)::date as visited_month
    from appointment a
    where a.tenant_id = :tenantId
      and a.customer_id = :customerId
      and a.estado in ('COMPLETED', 'COMPLETADA')
    order by visited_month desc
    """, nativeQuery = true)
    List<LocalDate> findVisitedMonths(
            @Param("tenantId") Long tenantId,
            @Param("customerId") Long customerId
    );

    @Query("""
        select count(a)
        from Appointment a
        where a.tenant.id = :tenantId
          and a.user.id = :barberId
          and a.fecha = :fecha
    """)
    long countTodayAppointments(Long tenantId, Long barberId, LocalDate fecha);

    @Query("""
        select count(a)
        from Appointment a
        where a.tenant.id = :tenantId
          and a.user.id = :barberId
          and a.fecha = :fecha
          and upper(a.estado) in ('ATENDIDO', 'COMPLETADO', 'FINALIZADO')
    """)
    long countTodayAttended(Long tenantId, Long barberId, LocalDate fecha);

    @Query("""
        select count(a)
        from Appointment a
        where a.tenant.id = :tenantId
          and a.user.id = :barberId
          and a.fecha = :fecha
          and upper(a.estado) = 'CANCELADO'
    """)
    long countTodayCancelled(Long tenantId, Long barberId, LocalDate fecha);

    @Query("""
    select a
    from Appointment a
    left join fetch a.customer c
    left join fetch a.service s
    where a.tenant.id = :tenantId
      and a.user.id = :barberId
      and a.fecha = :fecha
      and a.horaFin >= :horaActual
      and upper(a.estado) not in ('CANCELADO', 'ATENDIDO', 'COMPLETADO', 'FINALIZADO')
    order by a.horaInicio asc
""")
    List<Appointment> findUpcomingTodayAppointments(
            Long tenantId,
            Long barberId,
            LocalDate fecha,
            LocalTime horaActual
    );

    @Query(value = """
    select
        a.appointment_id as appointmentId,
        c.customer_id as customerId,
        trim(concat(coalesce(c.nombres, ''), ' ', coalesce(c.apellidos, ''))) as cliente,
        c.telefono as telefono,
        s.nombre as servicio,
        a.estado as estado,
        a.fecha as fecha,
        a.hora_inicio as horaInicio,
        a.hora_fin as horaFin
    from appointment a
    left join customer c on c.customer_id = a.customer_id
    left join service s on s.service_id = a.service_id
    where a.tenant_id = :tenantId
      and a.branch_id = :branchId
      and a.user_id = :userId
      and a.fecha = :fecha
    order by a.hora_inicio asc
""", nativeQuery = true)
    List<BarberAgendaProjection> findAgendaByTenantBranchUserAndFecha(
            @Param("tenantId") Long tenantId,
            @Param("branchId") Long branchId,
            @Param("userId") Long userId,
            @Param("fecha") LocalDate fecha
    );

    Optional<Appointment> findByIdAndTenant_IdAndBranch_IdAndUser_Id(
            Long appointmentId,
            Long tenantId,
            Long branchId,
            Long userId
    );


}
