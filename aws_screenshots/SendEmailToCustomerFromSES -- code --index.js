// Below is for sending the emails via SES
import { SESClient, SendEmailCommand } from "@aws-sdk/client-ses";

const sesClient = new SESClient({ region: "us-east-1" });

export const handler = async (event) => {

    console.log("📩 Event received:", JSON.stringify(event, null, 2));

    const messagedataFromSns = JSON.parse(event.Records?.[0]?.Sns?.Message) || "Default message";

    console.log("printing shipmentId & shipmentStatusAndDesc & comment:: " + messagedataFromSns.shipmentId + " -- " + messagedataFromSns.shipmentStatusAndDesc+ " -- " + messagedataFromSns.comment);

    const now = new Date();

    const istDateTime = now.toLocaleString("en-IN", {
        timeZone: "Asia/Kolkata",
        dateStyle: "medium",
        timeStyle: "long"
    });

    console.log(istDateTime);

    const shipmentId=messagedataFromSns.shipmentId ;
    const shipmentStatusAndDesc=messagedataFromSns.shipmentStatusAndDesc ;
    const comment=messagedataFromSns.comment ;


    const message = 'Shipment# ' + shipmentId + '<br/>Current Status: ' + shipmentStatusAndDesc + '<br/>Event DateTime: ' + istDateTime + '<br/>Comments: ' + comment;
    const params = {
        Source: "p1v2s3test@gmail.com", // must be verified in SES
        Destination: {
            ToAddresses: ["p1v2s3test@gmail.com"] // must be verified in sandbox
        },
        Message: {
            Subject: {
                Data: `SES Email Notification about Shipment ${shipmentId} status change !!`
            },
            Body: {
                Html: {
                    Data: `
                        <h2>Update on Shipment status </h2>
                        <h3>${message}</h3>
                        <br/>
                        <b>Sent via SES</b>
                    `
                },
                Text: {
                    Data: message
                }
            }
        }
    };

    try {
        const response = await sesClient.send(new SendEmailCommand(params));
        console.log("✅ Email sent via SES:", response);
    } catch (error) {
        console.error("❌ SES Error:", error);
        throw error;
    }

    return {
        statusCode: 200,
        body: "Email sent"
    };
};

// Below is for sending the emails via SNS 
// Not needed to send via Lambda, because we can achive that by directly creating an subscription in SNS
// We are using SES above, because SNS will send the raw message without any formatiing, but SES sends in proper mail-format

/* import { SNSClient, PublishCommand } from "@aws-sdk/client-sns";

// Create SNS client
const snsClient = new SNSClient({ region: "us-east-1" });

// Replace with your EMAIL SNS topic ARN
const EMAIL_TOPIC_ARN = "arn:aws:sns:us-east-1:953158925887:shipment-status-updates";

export const handler = async (event) => {

    console.log("🔥 SNS EVENT RECEIVED");
    console.log(JSON.stringify(event, null, 2));

    const message = event.Records[0].Sns.Message;

    console.log("📩 SNS Message:");
    console.log(message);

    try {
        // Publish to another SNS topic (which has email subscription)
        const params = {
            TopicArn: EMAIL_TOPIC_ARN,
            Message: message,
            Subject: "Forwarded SNS Message"
        };

        const response = await snsClient.send(new PublishCommand(params));

        console.log("✅ Email triggered via SNS. MessageId:", response.MessageId);

    } catch (error) {
        console.error("❌ Error sending email:", error);
        throw error; // ensures retry if needed
    }

    return {
        statusCode: 200,
        body: "Success"
    };
};
 */

