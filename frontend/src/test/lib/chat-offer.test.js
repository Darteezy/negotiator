import { parseSupplierMessage } from "@/lib/chat-offer";

describe("parseSupplierMessage", () => {
  it("extracts a complete structured offer from free text", () => {
    expect(
      parseSupplierMessage(
        "Price 104, payment 45 days, delivery 10 days, contract 12 months.",
      ),
    ).toMatchObject({
      complete: true,
      missingFields: [],
      outOfBounds: [],
      terms: {
        price: 104,
        paymentDays: 45,
        deliveryDays: 10,
        contractMonths: 12,
      },
      inheritedFields: [],
    });
  });

  it("reuses previous terms when the supplier only updates part of the offer", () => {
    expect(
      parseSupplierMessage("Please increase price to 104.", null, {
        price: 101,
        paymentDays: 40,
        deliveryDays: 9,
        contractMonths: 12,
      }),
    ).toMatchObject({
      complete: true,
      missingFields: [],
      outOfBounds: [],
      terms: {
        price: 104,
        paymentDays: 40,
        deliveryDays: 9,
        contractMonths: 12,
      },
      inheritedFields: ["paymentDays", "deliveryDays", "contractMonths"],
    });
  });

  it("uses the referenced buyer option as the base and applies a relative euro price change", () => {
    expect(
      parseSupplierMessage(
        "Option 3 but price 5 euro higher.",
        null,
        {
          price: 101,
          paymentDays: 40,
          deliveryDays: 9,
          contractMonths: 12,
        },
        [
          {
            price: 101,
            paymentDays: 40,
            deliveryDays: 9,
            contractMonths: 12,
          },
          {
            price: 102,
            paymentDays: 42,
            deliveryDays: 8,
            contractMonths: 12,
          },
          {
            price: 103,
            paymentDays: 45,
            deliveryDays: 7,
            contractMonths: 15,
          },
        ],
      ),
    ).toMatchObject({
      complete: true,
      optionReference: 3,
      terms: {
        price: 108,
        paymentDays: 45,
        deliveryDays: 7,
        contractMonths: 15,
      },
    });
  });
});
